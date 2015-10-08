package com.gentics.mesh.core.data.impl;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_FIELD_CONTAINER;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_ROLE;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_TAG;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_TAGFAMILY_ROOT;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_USER;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.DELETE_ACTION;
import static com.gentics.mesh.core.data.search.SearchQueueEntryAction.UPDATE_ACTION;
import static com.gentics.mesh.util.VerticleHelper.processOrFail2;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.List;

import com.gentics.mesh.api.common.PagingInfo;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.Page;
import com.gentics.mesh.core.data.IndexedVertex;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.TagGraphFieldContainer;
import com.gentics.mesh.core.data.generic.GenericFieldContainerNode;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.impl.NodeImpl;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.data.search.SearchQueueEntryAction;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.tag.TagFamilyReference;
import com.gentics.mesh.core.rest.tag.TagReference;
import com.gentics.mesh.core.rest.tag.TagResponse;
import com.gentics.mesh.core.rest.tag.TagUpdateRequest;
import com.gentics.mesh.etc.MeshSpringConfiguration;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.handler.InternalActionContext;
import com.gentics.mesh.util.InvalidArgumentException;
import com.gentics.mesh.util.TraversalHelper;
import com.syncleus.ferma.traversals.VertexTraversal;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TagImpl extends GenericFieldContainerNode<TagResponse>implements Tag, IndexedVertex {

	private static final Logger log = LoggerFactory.getLogger(TagImpl.class);

	public static final String DEFAULT_TAG_LANGUAGE_TAG = "en";

	@Override
	public String getType() {
		return Tag.TYPE;
	}

	@Override
	public List<? extends Node> getNodes() {
		return in(HAS_TAG).has(NodeImpl.class).toListExplicit(NodeImpl.class);
	}

	@Override
	public List<? extends TagGraphFieldContainer> getFieldContainers() {
		return out(HAS_FIELD_CONTAINER).has(TagGraphFieldContainerImpl.class).toListExplicit(TagGraphFieldContainerImpl.class);
	}

	@Override
	public TagGraphFieldContainer getFieldContainer(Language language) {
		return getGraphFieldContainer(language, TagGraphFieldContainerImpl.class);
	}

	@Override
	public TagGraphFieldContainer getOrCreateFieldContainer(Language language) {
		return getOrCreateGraphFieldContainer(language, TagGraphFieldContainerImpl.class);
	}

	@Override
	public String getName() {
		return getFieldContainer(BootstrapInitializer.getBoot().languageRoot().getTagDefaultLanguage()).getName();
	}

	@Override
	public void setName(String name) {
		getOrCreateFieldContainer(BootstrapInitializer.getBoot().languageRoot().getTagDefaultLanguage()).setName(name);
	}

	@Override
	public void removeNode(Node node) {
		unlinkIn(node.getImpl(), HAS_TAG);
	}

	@Override
	public Tag transformToRest(InternalActionContext ac, Handler<AsyncResult<TagResponse>> resultHandler) {

		Database db = MeshSpringConfiguration.getInstance().database();
		db.asyncNoTrx(tc -> {
			TagResponse restTag = new TagResponse();
			fillRest(restTag, ac);
			TagFamily tagFamily = getTagFamily();
			if (tagFamily != null) {
				TagFamilyReference tagFamilyReference = new TagFamilyReference();
				tagFamilyReference.setName(tagFamily.getName());
				tagFamilyReference.setUuid(tagFamily.getUuid());
				restTag.setTagFamily(tagFamilyReference);
			}
			restTag.getFields().setName(getName());
			tc.complete(restTag);
		} , (AsyncResult<TagResponse> rh) -> {
			resultHandler.handle(rh);
		});

		return this;
	}

	@Override
	public void setTagFamily(TagFamily tagFamily) {
		linkOut(tagFamily.getImpl(), HAS_TAGFAMILY_ROOT);
	}

	@Override
	public TagFamily getTagFamily() {
		return in(HAS_TAG).has(TagFamilyImpl.class).nextOrDefaultExplicit(TagFamilyImpl.class, null);
	}

	@Override
	public void delete() {
		if (log.isDebugEnabled()) {
			log.debug("Deleting tag {" + getName() + "}");
		}
		addIndexBatch(DELETE_ACTION);
		getVertex().remove();
	}

	@Override
	public Page<? extends Node> findTaggedNodes(MeshAuthUser requestUser, List<String> languageTags, PagingInfo pagingInfo)
			throws InvalidArgumentException {

		VertexTraversal<?, ?, ?> traversal = in(HAS_TAG).has(NodeImpl.class).mark().in(READ_PERM.label()).out(HAS_ROLE).in(HAS_USER)
				.retain(requestUser.getImpl()).back();
		VertexTraversal<?, ?, ?> countTraversal = in(HAS_TAG).has(NodeImpl.class).mark().in(READ_PERM.label()).out(HAS_ROLE).in(HAS_USER)
				.retain(requestUser.getImpl()).back();
		Page<? extends Node> nodePage = TraversalHelper.getPagedResult(traversal, countTraversal, pagingInfo, NodeImpl.class);
		return nodePage;
	}

	@Override
	public TagReference tansformToTagReference() {
		TagReference reference = new TagReference();
		reference.setUuid(getUuid());
		reference.setName(getName());
		return reference;
	}

	@Override
	public void update(InternalActionContext ac, Handler<AsyncResult<Void>> handler) {
		Database db = MeshSpringConfiguration.getInstance().database();

		TagUpdateRequest requestModel = ac.fromJson(TagUpdateRequest.class);
		TagFamilyReference reference = requestModel.getTagFamilyReference();
		db.trx(txUpdate -> {
			boolean updateTagFamily = false;
			if (reference != null) {
				// Check whether a uuid was specified and whether the tag family changed
				if (!isEmpty(reference.getUuid())) {
					if (!getTagFamily().getUuid().equals(reference.getUuid())) {
						updateTagFamily = true;
					}
				}
			}

			String newTagName = requestModel.getFields().getName();
			if (isEmpty(newTagName)) {
				txUpdate.fail(new HttpStatusCodeErrorException(BAD_REQUEST, ac.i18n("tag_name_not_set")));
				return;
			} else {
				TagFamily tagFamily = getTagFamily();
				Tag foundTagWithSameName = tagFamily.findTagByName(newTagName);
				if (foundTagWithSameName != null && !foundTagWithSameName.getUuid().equals(getUuid())) {
					txUpdate.fail(new HttpStatusCodeErrorException(CONFLICT,
							ac.i18n("tag_create_tag_with_same_name_already_exists", newTagName, tagFamily.getName())));
					return;
				}
				setEditor(ac.getUser());
				setLastEditedTimestamp(System.currentTimeMillis());
				setName(requestModel.getFields().getName());
				if (updateTagFamily) {
					// TODO update the tagfamily
				}
			}
			SearchQueueBatch batch = addIndexBatch(UPDATE_ACTION);
			txUpdate.complete(batch);
		} , (AsyncResult<SearchQueueBatch> txUpdated) -> {
			if (txUpdated.failed()) {
				handler.handle(Future.failedFuture(txUpdated.cause()));
			} else {
				processOrFail2(ac, txUpdated.result(), handler);
			}
		});

	}

	@Override
	public void addRelatedEntries(SearchQueueBatch batch, SearchQueueEntryAction action) {
		for (Node node : getNodes()) {
			batch.addEntry(node, UPDATE_ACTION);
		}
		batch.addEntry(getTagFamily(), UPDATE_ACTION);
	}

}
