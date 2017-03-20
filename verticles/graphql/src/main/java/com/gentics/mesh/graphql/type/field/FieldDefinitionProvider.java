package com.gentics.mesh.graphql.type.field;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static graphql.Scalars.GraphQLBigDecimal;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLInt;
import static graphql.Scalars.GraphQLLong;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.GraphFieldContainer;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.field.BinaryGraphField;
import com.gentics.mesh.core.data.node.field.BooleanGraphField;
import com.gentics.mesh.core.data.node.field.DateGraphField;
import com.gentics.mesh.core.data.node.field.HtmlGraphField;
import com.gentics.mesh.core.data.node.field.NumberGraphField;
import com.gentics.mesh.core.data.node.field.StringGraphField;
import com.gentics.mesh.core.data.node.field.nesting.MicronodeGraphField;
import com.gentics.mesh.core.data.node.field.nesting.NodeGraphField;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.ListFieldSchema;
import com.gentics.mesh.graphql.type.AbstractTypeProvider;
import com.gentics.mesh.graphql.type.MicronodeFieldTypeProvider;
import com.gentics.mesh.util.DateUtils;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;

@Singleton
public class FieldDefinitionProvider extends AbstractTypeProvider {

	@Inject
	public MicronodeFieldTypeProvider micronodeFieldTypeProvider;

	@Inject
	public FieldDefinitionProvider() {
	}

	public GraphQLObjectType createBinaryFieldType() {
		Builder type = newObject().name("BinaryField")
				.description("Binary field");

		// .fileName
		type.field(newFieldDefinition().name("fileName")
				.description("Filename of the uploaded file.")
				.type(GraphQLString)
				.build());

		// .width
		type.field(newFieldDefinition().name("width")
				.description("Image width in pixel.")
				.type(GraphQLInt)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof BinaryGraphField) {
						return ((BinaryGraphField) source).getImageWidth();
					}
					return null;
				})
				.build());
		// .height
		type.field(newFieldDefinition().name("height")
				.description("Image height in pixel.")
				.type(GraphQLInt)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof BinaryGraphField) {
						return ((BinaryGraphField) source).getImageHeight();
					}
					return null;
				})
				.build());

		// .sha512sum
		type.field(newFieldDefinition().name("sha512sum")
				.description("SHA512 checksum of the binary data.")
				.type(GraphQLString)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof BinaryGraphField) {
						return ((BinaryGraphField) source).getSHA512Sum();
					}
					return null;
				})
				.build());

		// .fileSize
		type.field(newFieldDefinition().name("fileSize")
				.description("Size of the binary data in bytes")
				.type(GraphQLLong)
				.build());

		// .mimeType
		type.field(newFieldDefinition().name("mimeType")
				.description("Mimetype of the binary data")
				.type(GraphQLString)
				.build());

		// .dominantColor
		type.field(newFieldDefinition().name("dominantColor")
				.description("Computed image dominant color")
				.type(GraphQLString)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof BinaryGraphField) {
						return ((BinaryGraphField) source).getImageDominantColor();
					}
					return null;
				})
				.build());

		return type.build();
	}

	public GraphQLFieldDefinition createBinaryDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(createBinaryFieldType())
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						return nodeContainer.getBinary(schema.getName());
					}
					return null;
				})
				.build();

	}

	public GraphQLFieldDefinition createBooleanDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(GraphQLBoolean)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						BooleanGraphField booleanField = nodeContainer.getBoolean(schema.getName());
						if (booleanField != null) {
							return booleanField.getBoolean();
						}
					}
					return null;
				})
				.build();
	}

	public GraphQLFieldDefinition createNumberDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(GraphQLBigDecimal)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						NumberGraphField numberField = nodeContainer.getNumber(schema.getName());
						if (numberField != null) {
							return numberField.getNumber();
						}
					}
					return null;
				})
				.build();
	}

	public GraphQLFieldDefinition createHtmlDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(GraphQLString)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						HtmlGraphField htmlField = nodeContainer.getHtml(schema.getName());
						if (htmlField != null) {
							return htmlField.getHTML();
						}
					}
					return null;
				})
				.build();
	}

	public GraphQLFieldDefinition createStringDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(GraphQLString)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof GraphFieldContainer) {
						GraphFieldContainer nodeContainer = (GraphFieldContainer) source;
						StringGraphField field = nodeContainer.getString(schema.getName());
						if (field != null) {
							return field.getString();
						}
					}
					return null;
				})
				.build();
	}

	public GraphQLFieldDefinition createDateDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(GraphQLString)
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						DateGraphField dateField = nodeContainer.getDate(schema.getName());
						if (dateField != null) {
							return DateUtils.toISO8601(dateField.getDate(), 0);
						}
					}
					return null;
				})
				.build();
	}

	/**
	 * Create the graphql field definition for the given list field schema.
	 * 
	 * @param schema
	 * @return
	 */
	public GraphQLFieldDefinition createListDef(ListFieldSchema schema) {
		GraphQLType type = getElementTypeOfList(schema);
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(new GraphQLList(type))
				.argument(getPagingArgs())
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;

						switch (schema.getListType()) {
						case "boolean":
							return nodeContainer.getBooleanList(schema.getName())
									.getList()
									.stream()
									.map(item -> item.getBoolean())
									.collect(Collectors.toList());
						case "html":
							return nodeContainer.getHTMLList(schema.getName())
									.getList()
									.stream()
									.map(item -> item.getHTML())
									.collect(Collectors.toList());
						case "string":
							return nodeContainer.getStringList(schema.getName())
									.getList()
									.stream()
									.map(item -> item.getString())
									.collect(Collectors.toList());
						case "number":
							return nodeContainer.getNumberList(schema.getName())
									.getList()
									.stream()
									.map(item -> item.getNumber())
									.collect(Collectors.toList());
						case "date":
							return nodeContainer.getDateList(schema.getName())
									.getList()
									.stream()
									.map(item -> DateUtils.toISO8601(item.getDate(), 0))
									.collect(Collectors.toList());
						case "node":
							return nodeContainer.getNodeList(schema.getName())
									.getList()
									.stream()
									.map(item -> item.getNode())
									.collect(Collectors.toList());
						case "micronode":
							return null;
						default:
							return null;
						}

					}
					return null;
				})
				.build();
	}

	private GraphQLType getElementTypeOfList(ListFieldSchema schema) {
		switch (schema.getListType()) {
		case "boolean":
			return GraphQLBoolean;
		case "html":
			return GraphQLString;
		case "string":
			return GraphQLString;
		case "number":
			return GraphQLBigDecimal;
		case "date":
			return GraphQLString;
		case "node":
			return new GraphQLTypeReference("Node");
		case "micronode":
			return new GraphQLTypeReference("Micronode");
		default:
			return null;
		}
	}

	public GraphQLFieldDefinition createMicronodeDef(FieldSchema schema, Project project) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(micronodeFieldTypeProvider.getMicroschemaFieldsType(project))
				.dataFetcher(fetcher -> {
					Object source = fetcher.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						MicronodeGraphField micronodeField = nodeContainer.getMicronode(schema.getName());
						if (micronodeField != null) {
							return micronodeField.getMicronode();
						}
					}
					return null;
				})
				.build();
	}

	/**
	 * Generate a new node field definition using the provided field schema.
	 * 
	 * @param schema
	 * @return
	 */
	public GraphQLFieldDefinition createNodeDef(FieldSchema schema) {
		return newFieldDefinition().name(schema.getName())
				.description(schema.getLabel())
				.type(new GraphQLTypeReference("Node"))
				.dataFetcher(env -> {
					Object source = env.getSource();
					if (source instanceof NodeGraphFieldContainer) {
						InternalActionContext ac = (InternalActionContext) env.getContext();
						NodeGraphFieldContainer nodeContainer = (NodeGraphFieldContainer) source;
						NodeGraphField nodeField = nodeContainer.getNode(schema.getName());
						if (nodeField != null) {
							Node node = nodeField.getNode();

							// Check permissions for the linked node
							if (node != null && (ac.getUser()
									.hasPermission(node, READ_PERM)
									|| ac.getUser()
											.hasPermission(node, GraphPermission.READ_PUBLISHED_PERM))) {
								return node;
							}
						}
					}
					return null;
				})
				.build();
	}

}
