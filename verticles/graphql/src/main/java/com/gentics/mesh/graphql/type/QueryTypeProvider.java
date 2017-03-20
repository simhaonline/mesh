package com.gentics.mesh.graphql.type;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.service.WebRootService;
import com.gentics.mesh.path.Path;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;

/**
 * The {@link QueryTypeProvider} provides as the name suggests the query type for the GraphQL schema. This type is the starting point for all GraphQL queries.
 * Various other schema types are located in dedicated classes for each type. Dependency injection is used to load those dependencies and thus make these types
 * accessible by the root type. Please note that this root type is and will most likely always be project specific. It is not possible to query other projects.
 * Only the currently selected project and global elements (user, roles, groups..) can be queries.
 * 
 * We must enforce this limitation because GraphQL schemas can't handle dynamic types. It is not possible to distinguish between a content schema v1 or projectA
 * and content schema v2 of projectB. It is not possible to access data across releases due to the same reason. A different release may use different schema
 * versions.
 */
@Singleton
public class QueryTypeProvider extends AbstractTypeProvider {

	@Inject
	public NodeFieldTypeProvider nodeFieldTypeProvider;

	@Inject
	public NodeTypeProvider nodeTypeProvider;

	@Inject
	public ProjectTypeProvider projectTypeProvider;

	@Inject
	public UserTypeProvider userTypeProvider;

	@Inject
	public TagTypeProvider tagTypeProvider;

	@Inject
	public TagFamilyTypeProvider tagFamilyTypeProvider;

	@Inject
	public RoleTypeProvider roleTypeProvider;

	@Inject
	public GroupTypeProvider groupTypeProvider;

	@Inject
	public WebRootService webrootService;

	@Inject
	public BootstrapInitializer boot;

	@Inject
	public ReleaseTypeProvider releaseTypeProvider;

	@Inject
	public SchemaTypeProvider schemaTypeProvider;

	@Inject
	public MicroschemaTypeProvider microschemaTypeProvider;

	@Inject
	public QueryTypeProvider() {
	}

	/**
	 * Data fetcher for nodes.
	 * 
	 * @param env
	 * @return
	 */
	public Object nodeFetcher(DataFetchingEnvironment env) {
		String uuid = env.getArgument("uuid");
		if (uuid != null) {
			InternalActionContext ac = (InternalActionContext) env.getContext();
			Node node = boot.nodeRoot()
					.findByUuid(uuid);
			// Check permissions
			if (ac.getUser()
					.hasPermission(node, GraphPermission.READ_PERM)
					|| ac.getUser()
							.hasPermission(node, GraphPermission.READ_PUBLISHED_PERM)) {
				return node;
			}
		}

		String path = env.getArgument("path");
		if (path != null) {
			InternalActionContext ac = (InternalActionContext) env.getContext();
			Path pathResult = webrootService.findByProjectPath(ac, path);
			return pathResult.getLast()
					.getNode();
		}
		return null;
	}

	/**
	 * Data fetcher for the currently active user.
	 * 
	 * @param env
	 * @return
	 */
	public Object userMeFetcher(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof InternalActionContext) {
			InternalActionContext ac = (InternalActionContext) source;
			MeshAuthUser requestUser = ac.getUser();
			// No need to check for permissions. The user should always be able to read himself
			return requestUser;
		}
		return null;
	}

	/**
	 * Data fetcher for the current project.
	 * 
	 * @param env
	 * @return
	 */
	public Object projectFetcher(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof InternalActionContext) {
			InternalActionContext ac = (InternalActionContext) source;
			MeshAuthUser requestUser = ac.getUser();
			Project project = ac.getProject();
			if (requestUser.hasPermission(project, READ_PERM)) {
				return project;
			}
		}
		return null;
	}

	/**
	 * Data fetcher for the root node of the current project.
	 * 
	 * @param env
	 * @return
	 */
	public Object rootNodeFetcher(DataFetchingEnvironment env) {
		Object source = env.getSource();
		if (source instanceof InternalActionContext) {
			InternalActionContext ac = (InternalActionContext) source;
			Project project = ac.getProject();
			if (project != null) {
				Node node = project.getBaseNode();
				if (ac.getUser()
						.hasPermission(node, GraphPermission.READ_PERM)
						|| ac.getUser()
								.hasPermission(node, GraphPermission.READ_PUBLISHED_PERM)) {
					return node;
				}
			}
		}
		return null;
	}

	/**
	 * Construct the query/root type for the current project.
	 * 
	 * @param project
	 * @return
	 */
	public GraphQLObjectType getRootType(Project project) {
		Builder root = newObject();
		root.name("Query");

		// .me
		root.field(newFieldDefinition().name("me")
				.description("The current user")
				.type(userTypeProvider.getUserType())
				.dataFetcher(this::userMeFetcher)
				.build());

		//.project
		root.field(newFieldDefinition().name("project")
				.description("Load project current")
				.type(projectTypeProvider.getProjectType(project))
				.dataFetcher(this::projectFetcher)
				.build());

		// NOT ALLOWED - See class description for details
		// .projects
		//	root.field(newPagingField("projects", "Load page of projects.", (ac) -> boot.projectRoot(), "Project"));

		// .node
		root.field(newFieldDefinition().name("node")
				.description("Load a node by uuid or webroot path.")
				.argument(getUuidArg("Node uuid"))
				.argument(getPathArg())
				.dataFetcher(this::nodeFetcher)
				.type(nodeTypeProvider.getNodeType(project))
				.build());

		// .nodes
		root.field(newPagingField("nodes", "Load page of nodes.", (ac) -> ac.getProject()
				.getNodeRoot(), "Node"));

		// .baseNode
		root.field(newFieldDefinition().name("rootNode")
				.description("Return the project root node.")
				.type(new GraphQLTypeReference("Node"))
				.dataFetcher(this::rootNodeFetcher)
				.build());

		// .tag
		root.field(newElementField("tag", "Load tag by name or uuid.", (ac) -> boot.tagRoot(), tagTypeProvider.createTagType()));

		// .tags
		root.field(newPagingField("tags", "Load page of tags.", (ac) -> boot.tagRoot(), "Tag"));

		// .tagFamily
		root.field(newElementField("tagFamily", "Load tagFamily by name or uuid.", (ac) -> ac.getProject()
				.getTagFamilyRoot(), tagFamilyTypeProvider.getTagFamilyType()));

		// .tagFamilies
		root.field(newPagingField("tagFamilies", "Load page of tagFamilies.", (ac) -> boot.tagFamilyRoot(), "TagFamily"));

		// .release
		root.field(newElementField("release", "Load release by name or uuid.", (ac) -> ac.getProject()
				.getReleaseRoot(), releaseTypeProvider.getReleaseType()));

		//.releases
		root.field(newPagingField("releases", "Load page of releases.", (ac) -> ac.getProject()
				.getReleaseRoot(), "Release"));

		// .schema
		root.field(newElementField("schema", "Load schema by name or uuid.", (ac) -> boot.schemaContainerRoot(), schemaTypeProvider.getSchemaType()));

		// .schemas
		root.field(newPagingField("schemas", "Load page of schemas.", (ac) -> boot.schemaContainerRoot(), "Schema"));

		// .microschema
		root.field(newElementField("microschema", "Load microschema by name or uuid.", (ac) -> boot.microschemaContainerRoot(),
				microschemaTypeProvider.createMicroschemaType()));

		// .microschemas
		root.field(newPagingField("microschemas", "Load page of microschemas.", (ac) -> boot.microschemaContainerRoot(), "Microschema"));

		// .role
		root.field(newElementField("role", "Load role by name or uuid.", (ac) -> boot.roleRoot(), roleTypeProvider.createRoleType()));

		// .roles
		root.field(newPagingField("roles", "Load page of roles.", (ac) -> boot.roleRoot(), "Role"));

		// .group
		root.field(newElementField("group", "Load group by name or uuid.", (ac) -> boot.groupRoot(), groupTypeProvider.createGroupType()));

		// .groups
		root.field(newPagingField("groups", "Load page of groups.", (ac) -> boot.groupRoot(), "Group"));

		// .user
		root.field(newElementField("user", "Load user by name or uuid.", (ac) -> boot.userRoot(), userTypeProvider.getUserType()));

		// .users
		root.field(newPagingField("users", "Load page of users.", (ac) -> boot.userRoot(), "User"));

		return root.build();
	}

	/**
	 * Construct the root schema.
	 * 
	 * @param project
	 * @return
	 */
	public GraphQLSchema getRootSchema(Project project) {
		graphql.schema.GraphQLSchema.Builder schema = GraphQLSchema.newSchema();
		return schema.query(getRootType(project))
				.build();
	}

}
