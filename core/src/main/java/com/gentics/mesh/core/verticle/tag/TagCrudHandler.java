package com.gentics.mesh.core.verticle.tag;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.util.VerticleHelper.createObject;
import static com.gentics.mesh.util.VerticleHelper.deleteObject;
import static com.gentics.mesh.util.VerticleHelper.hasSucceeded;
import static com.gentics.mesh.util.VerticleHelper.loadObject;
import static com.gentics.mesh.util.VerticleHelper.loadTransformAndResponde;
import static com.gentics.mesh.util.VerticleHelper.transformAndResponde;
import static com.gentics.mesh.util.VerticleHelper.updateObject;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import org.springframework.stereotype.Component;

import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.rest.node.NodeListResponse;
import com.gentics.mesh.core.rest.tag.TagListResponse;
import com.gentics.mesh.core.verticle.handler.AbstractCrudHandler;
import com.gentics.mesh.handler.InternalActionContext;

@Component
public class TagCrudHandler extends AbstractCrudHandler {

	@Override
	public void handleCreate(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			createObject(ac, boot.tagRoot());
		} , ac.errorHandler());
	}

	@Override
	public void handleDelete(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			deleteObject(ac, "uuid", "tag_deleted", ac.getProject().getTagRoot());
		} , ac.errorHandler());
	}

	@Override
	public void handleUpdate(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			updateObject(ac, "uuid", ac.getProject().getTagRoot());
		} , ac.errorHandler());
	}

	@Override
	public void handleRead(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			Project project = ac.getProject();
			loadTransformAndResponde(ac, "uuid", READ_PERM, project.getTagRoot());
		} , ac.errorHandler());
	}

	@Override
	public void handleReadList(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			Project project = ac.getProject();
			loadTransformAndResponde(ac, project.getTagRoot(), new TagListResponse());
		} , ac.errorHandler());
	}

	/**
	 * Add the handler that returns a node list for a specified tag.
	 * 
	 * @param ac
	 */
	public void handleTaggedNodesList(InternalActionContext ac) {
		db.asyncNoTrx(tc -> {
			Project project = ac.getProject();
			loadObject(ac, "uuid", READ_PERM, project.getTagRoot(), rh -> {
				if (hasSucceeded(ac, rh)) {
					Tag tag = rh.result();
					Page<? extends Node> page;
					try {
						page = tag.findTaggedNodes(ac.getUser(), ac.getSelectedLanguageTags(), ac.getPagingInfo());
						transformAndResponde(ac, page, new NodeListResponse());
					} catch (Exception e) {
						//TODO i18n - exception handling
						ac.fail(BAD_REQUEST, "Could not load nodes");
					}
				}
			});
		} , ac.errorHandler());
	}

}
