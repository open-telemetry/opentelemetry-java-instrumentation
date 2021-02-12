/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.of;

import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;

public enum DynamoDbRequest {
  CreateTable(
      "CreateTableRequest",
      of("awssdk.global_secondary_indexes", "globalSecondaryIndexes"),
      of("awssdk.local_secondary_indexes", "localSecondaryIndexes"),
      of(
          "awssdk.provisioned_throughput.read_capacity_units",
          "provisionedThroughput.readCapacityUnits"),
      of(
          "awssdk.provisioned_throughput.write_capacity_units",
          "provisionedThroughput.writeCapacityUnits"));

  private final String requestClass;
  private final FieldMapping[] fields;

  DynamoDbRequest(String requestClass, FieldMapping... fields) {
    this.requestClass = requestClass;
    this.fields = fields;
  }

  @Nullable
  static DynamoDbRequest ofSdkRequest(SdkRequest request) {
    // exact request class should be 1st level child of request type
    String typeName = request.getClass().getSimpleName();
    for (DynamoDbRequest type : values()) {
      if (type.requestClass.equals(typeName)) {
        return type;
      }
    }
    return null;
  }

  public FieldMapping[] fields() {
    return fields;
  }
}
