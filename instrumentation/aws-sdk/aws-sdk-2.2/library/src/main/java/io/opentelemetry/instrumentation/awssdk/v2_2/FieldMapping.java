/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

class FieldMapping {

  enum Type {
    REQUEST,
    RESPONSE;
  }

  static FieldMapping request(String attribute, String fieldPath) {
    return new FieldMapping(Type.REQUEST, attribute, fieldPath);
  }

  static FieldMapping response(String attribute, String fieldPath) {
    return new FieldMapping(Type.RESPONSE, attribute, fieldPath);
  }

  FieldMapping(Type type, String attribute, String fieldPath) {
    this.type = type;
    this.attribute = attribute;
    this.fieldPath = fieldPath;
    this.fields = fieldPath.split("\\.");
  }

  String getAttribute() {
    return attribute;
  }

  String[] getFields() {
    return fields;
  }

  Type getType() {
    return type;
  }

  private final Type type;
  private final String attribute;
  private final String fieldPath;
  private final String[] fields;

  public static final Map<Type, List<FieldMapping>> map(FieldMapping[] fieldMappings) {

    EnumMap<Type, List<FieldMapping>> fields = new EnumMap<>(Type.class);
    for (FieldMapping.Type type : FieldMapping.Type.values()) {
      fields.put(type, new ArrayList<>());
    }
    for (FieldMapping fieldMapping : fieldMappings) {
      fields.get(fieldMapping.getType()).add(fieldMapping);
    }
    return fields;
  }
}
