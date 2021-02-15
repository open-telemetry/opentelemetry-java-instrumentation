/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DynamoDB;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.Kinesis;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.S3;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.SQS;
import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.of;

import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;

/**
 * Temporary solution - maps only DynamoDB attributes. Final solution should be generated from AWS
 * SDK automatically
 * (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2291).
 */
public enum AwsSdkRequest {
  // generic requests
  DynamoDbRequest(DynamoDB, "DynamoDbRequest"),
  S3Request(S3, "S3Request"),
  SqsRequest(SQS, "SqsRequest"),
  KinesisRequest(Kinesis, "KinesisRequest"),
  // specific requests
  CreateTable(
      DynamoDB,
      "CreateTableRequest",
      of("awssdk.global_secondary_indexes", "globalSecondaryIndexes"),
      of("awssdk.local_secondary_indexes", "localSecondaryIndexes"),
      of(
          "awssdk.provisioned_throughput.read_capacity_units",
          "provisionedThroughput.readCapacityUnits"),
      of(
          "awssdk.provisioned_throughput.write_capacity_units",
          "provisionedThroughput.writeCapacityUnits"));

  private final AwsSdkRequestType type;
  private final String requestClass;
  private final FieldMapping[] fields;

  AwsSdkRequest(AwsSdkRequestType type, String requestClass, FieldMapping... fields) {
    this.type = type;
    this.requestClass = requestClass;
    this.fields = fields;
  }

  @Nullable
  static AwsSdkRequest ofSdkRequest(SdkRequest request) {
    // try request type
    AwsSdkRequest result = ofType(request.getClass().getSimpleName());
    // try parent - generic
    if (result == null) {
      result = ofType(request.getClass().getSuperclass().getSimpleName());
    }
    return result;
  }

  private static AwsSdkRequest ofType(String typeName) {
    for (AwsSdkRequest type : values()) {
      if (type.requestClass.equals(typeName)) {
        return type;
      }
    }
    return null;
  }

  public FieldMapping[] fields() {
    return fields;
  }

  public AwsSdkRequestType type() {
    return type;
  }
}
