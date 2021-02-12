/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

class FieldMapping {
  static FieldMapping of(String attribute, String fieldPath) {
    return new FieldMapping(attribute, fieldPath);
  }

  FieldMapping(String attribute, String fieldPath) {
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

  private final String attribute;
  private final String fieldPath;
  private final String[] fields;
}
