package com.gentics.mesh.core.data.job.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_PROJECT;
import static com.gentics.mesh.core.rest.MeshEvent.PROJECT_VERSION_PURGE_FINISHED;
import static com.gentics.mesh.core.rest.job.JobStatus.COMPLETED;
import static com.gentics.mesh.core.rest.job.JobStatus.FAILED;

import java.time.ZonedDateTime;
import java.util.Optional;

import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.generic.MeshVertexImpl;
import com.gentics.mesh.core.data.impl.ProjectImpl;
import com.gentics.mesh.core.project.maintenance.ProjectVersionPurgeHandler;
import com.gentics.mesh.core.rest.MeshEvent;
import com.gentics.mesh.core.rest.event.job.ProjectVersionPurgeEventModel;
import com.gentics.mesh.core.rest.job.JobStatus;
import com.gentics.mesh.dagger.DB;
import com.gentics.mesh.dagger.MeshInternal;
import com.gentics.mesh.event.EventQueueBatch;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.graphdb.spi.IndexHandler;
import com.gentics.mesh.graphdb.spi.TypeHandler;
import com.gentics.mesh.util.DateUtils;

import io.reactivex.Completable;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class VersionPurgeJobImpl extends JobImpl {

	private static final Logger log = LoggerFactory.getLogger(VersionPurgeJobImpl.class);

	private static final String MAX_AGE_PROPERTY = "maxAge";

	public static void init(TypeHandler type, IndexHandler index) {
		type.createVertexType(VersionPurgeJobImpl.class, MeshVertexImpl.class);
	}

	public Project getProject() {
		return out(HAS_PROJECT).nextOrDefaultExplicit(ProjectImpl.class, null);
	}

	public void setProject(Project project) {
		setSingleLinkOutTo(project, HAS_PROJECT);
	}

	public Optional<ZonedDateTime> getMaxAge() {
		Long maxAge = getProperty(MAX_AGE_PROPERTY);
		return Optional.ofNullable(maxAge).map(DateUtils::toZonedDateTime);
	}

	public void setMaxAge(ZonedDateTime time) {
		if (time != null) {
			Long ms = time.toInstant().toEpochMilli();
			setProperty(MAX_AGE_PROPERTY, ms);
		} else {
			removeProperty(MAX_AGE_PROPERTY);
		}
	}

	@Override
	protected Completable processTask() {
		Database db = DB.get();
		ProjectVersionPurgeHandler handler = MeshInternal.get().projectVersionPurgeHandler();
		Project project = db.tx(() -> getProject());
		Optional<ZonedDateTime> maxAge = db.tx(() -> getMaxAge());
		return handler.purgeVersions(project, maxAge.orElse(null))
			.doOnComplete(() -> {
				db.tx(() -> {
					setStopTimestamp();
					setStatus(COMPLETED);
				});
				db.tx(() -> {
					log.info("Version purge job {" + getUuid() + "} for project {" + project.getName() + "} completed.");
					EventQueueBatch.create().add(createEvent(PROJECT_VERSION_PURGE_FINISHED, COMPLETED, project.getName(), project.getUuid()))
						.dispatch();
				});
			}).doOnError(error -> {
				db.tx(() -> {
					setStopTimestamp();
					setStatus(FAILED);
					setError(error);
				});
				db.tx(() -> {
					log.info("Version purge job {" + getUuid() + "} for project {" + project.getName() + "} failed.", error);
					EventQueueBatch.create().add(createEvent(PROJECT_VERSION_PURGE_FINISHED, FAILED, project.getName(), project.getUuid()))
						.dispatch();
				});
			});
	}

	private ProjectVersionPurgeEventModel createEvent(MeshEvent event, JobStatus status, String name, String uuid) {
		ProjectVersionPurgeEventModel model = new ProjectVersionPurgeEventModel();
		model.setName(name);
		model.setUuid(uuid);
		model.setEvent(event);
		model.setStatus(status);
		return model;
	}
}
