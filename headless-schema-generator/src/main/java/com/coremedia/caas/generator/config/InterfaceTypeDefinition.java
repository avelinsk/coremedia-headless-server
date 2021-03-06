package com.coremedia.caas.generator.config;

import com.coremedia.cap.common.CapPropertyDescriptor;
import com.coremedia.cap.content.ContentType;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InterfaceTypeDefinition extends AbstractTypeDefinition {

  public InterfaceTypeDefinition(ContentType contentType, SchemaConfig schemaConfig) {
    super(contentType, schemaConfig);
  }


  @Override
  public String getName() {
    return super.getName(null);
  }


  public InterfaceTypeDefinition getParent() {
    return getSchemaConfig().findParent(this);
  }


  @Override
  public List<InterfaceTypeDefinition> getInterfaceDefinitions() {
    return ImmutableList.of(getParent());
  }


  @Override
  public List<FieldDefinition> getFieldDefinitions() throws InvalidTypeDefinition {
    TypeCustomization typeCustomization = getTypeCustomization();
    ArrayList<FieldDefinition> result = Lists.newArrayList();
    for (String name : getFieldNames()) {
      if (typeCustomization != null && typeCustomization.hasCustomField(name)) {
        result.add(typeCustomization.getCustomField(name));
      } else if (typeCustomization == null || !typeCustomization.getExcludedProperties().contains(name)) {
        result.add(new DocumentFieldDefinition(getSchemaConfig(), getContentType().getDescriptor(name)));
      }
    }
    result.sort(Comparator.comparing(FieldDefinition::getName));
    return result;
  }


  private Set<String> getParentFieldNames() throws InvalidTypeDefinition {
    InterfaceTypeDefinition parent = getParent();
    HashSet<String> result = Sets.newHashSet();
    while (parent != null) {
      result.addAll(parent.getFieldNames());
      parent = parent.getParent();
    }
    return result;
  }

  private Set<String> getFieldNames() throws InvalidTypeDefinition {
    Set<String> parentNames = getParentFieldNames();
    // get custom field names
    Set<String> customNames = new HashSet<>();
    TypeCustomization typeCustomization = getTypeCustomization();
    if (typeCustomization != null) {
      customNames = typeCustomization.getCustomFields().stream().map(FieldDefinition::getName).collect(Collectors.toSet());
    }
    for (String name : customNames) {
      // customizations may redefine local fields but must never change a parent field
      if (parentNames.contains(name)) {
        throw new InvalidTypeDefinition("Cannot override field " + name + " for type " + getName());
      }
    }
    Set<String> localNames = getContentType().getDescriptors().stream().map(CapPropertyDescriptor::getName).collect(Collectors.toSet());
    // remove all fields already defined by the parent
    localNames.removeAll(parentNames);
    // add custom fields
    localNames.addAll(customNames);
    return localNames;
  }


  @Override
  public void validate() throws InvalidTypeDefinition {
    // ensure interface fields are not redefined
    getFieldNames();
  }


  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .add("name", getName())
            .toString();
  }
}
