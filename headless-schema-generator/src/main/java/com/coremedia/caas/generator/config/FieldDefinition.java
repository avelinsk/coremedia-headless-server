package com.coremedia.caas.generator.config;

import java.util.List;

public interface FieldDefinition {

  boolean isNonNull();

  String getName();

  String getSourceName();

  List<String> getFallbackSourceNames();

  String getTargetType();

  String getTypeName();
}
