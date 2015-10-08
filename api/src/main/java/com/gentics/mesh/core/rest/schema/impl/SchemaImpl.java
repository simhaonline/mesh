package com.gentics.mesh.core.rest.schema.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gentics.mesh.core.rest.common.RestModel;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.json.MeshJsonException;

public class SchemaImpl implements RestModel, Schema {

	private String name;
	private String description;
	private String displayField;
	private boolean binary = false;
	private boolean folder = false;
	private String meshVersion;
	private List<FieldSchema> fields = new ArrayList<>();

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getDisplayField() {
		return displayField;
	}

	@Override
	public void setDisplayField(String displayField) {
		this.displayField = displayField;
	}

	@Override
	public boolean isFolder() {
		return folder;
	}

	@Override
	public void setFolder(boolean flag) {
		this.folder = flag;
	}

	@Override
	public boolean isBinary() {
		return binary;
	}

	@Override
	public void setBinary(boolean flag) {
		this.binary = flag;
	}

	@Override
	public List<FieldSchema> getFields() {
		return fields;
	}

	@Override
	public String getMeshVersion() {
		return meshVersion;
	}

	@Override
	public void setMeshVersion(String meshVersion) {
		this.meshVersion = meshVersion;
	}

	@Override
	public void addField(FieldSchema fieldSchema) {
		this.fields.add(fieldSchema);
	}

	@Override
	public void validate() throws MeshJsonException {
		//TODO make sure that the display name field only maps to string fields since NodeImpl can currently only deal with string field values for displayNames 

		Set<String> fieldNames = new HashSet<>();
		Set<String> fieldLabels = new HashSet<>();
		for (FieldSchema fieldSchema : fields) {
			String name = fieldSchema.getName();
			String label = fieldSchema.getLabel();
			if (fieldNames.contains(name)) {
				throw new MeshJsonException("The schema contains duplicate names. The name for a schema field must be unique.");
			} else {
				fieldNames.add(name);
			}
			if (fieldLabels.contains(label)) {
				throw new MeshJsonException("The schema contains duplicate labels. The label for a schema field must be unique.");
			} else {
				fieldLabels.add(label);
			}
		}

	}

}
