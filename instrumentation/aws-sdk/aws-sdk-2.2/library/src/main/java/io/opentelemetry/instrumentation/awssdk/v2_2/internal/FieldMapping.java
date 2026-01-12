/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FieldMapping {

  enum Type {
    REQUEST,
    RESPONSE
  }

  private static final Set<AttributeType> SUPPORTED_ATTRIBUTE_TYPES =
      EnumSet.of(
          AttributeType.STRING,
          AttributeType.BOOLEAN,
          AttributeType.LONG,
          AttributeType.DOUBLE,
          AttributeType.STRING_ARRAY);

  private final Type type;
  private final AttributeKey<?> attributeKey;
  private final List<String> fields;
  private final boolean experimental;

  static FieldMapping request(AttributeKey<?> attributeKey, String fieldPath) {
    return request(attributeKey, fieldPath, false);
  }

  static FieldMapping request(
      AttributeKey<?> attributeKey, String fieldPath, boolean experimental) {
    return new FieldMapping(Type.REQUEST, attributeKey, fieldPath, experimental);
  }

  static FieldMapping response(AttributeKey<?> attributeKey, String fieldPath) {
    return response(attributeKey, fieldPath, false);
  }

  static FieldMapping response(
      AttributeKey<?> attributeKey, String fieldPath, boolean experimental) {
    return new FieldMapping(Type.RESPONSE, attributeKey, fieldPath, experimental);
  }

  static FieldMapping requestExperimental(AttributeKey<?> attributeKey, String fieldPath) {
    return request(attributeKey, fieldPath, true);
  }

  static FieldMapping responseExperimental(AttributeKey<?> attributeKey, String fieldPath) {
    return response(attributeKey, fieldPath, true);
  }

  FieldMapping(Type type, AttributeKey<?> attributeKey, String fieldPath, boolean experimental) {
    if (!SUPPORTED_ATTRIBUTE_TYPES.contains(attributeKey.getType())) {
      throw new IllegalArgumentException("Unsupported attribute type: " + attributeKey.getType());
    }
    this.type = type;
    this.attributeKey = attributeKey;
    this.fields = Collections.unmodifiableList(Arrays.asList(fieldPath.split("\\.")));
    this.experimental = experimental;
  }

  AttributeType getAttributeType() {
    return attributeKey.getType();
  }

  @SuppressWarnings("unchecked") // we expect the caller to check the attribute type
  <T> AttributeKey<T> getAttributeKey() {
    return (AttributeKey<T>) attributeKey;
  }

  List<String> getFields() {
    return fields;
  }

  Type getType() {
    return type;
  }

  boolean isExperimental() {
    return experimental;
  }

  static Map<Type, List<FieldMapping>> groupByType(FieldMapping[] fieldMappings) {
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
