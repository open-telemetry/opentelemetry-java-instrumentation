/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.SdkRequest;

enum RequestType {
  S3("S3Request", "Bucket"),
  SQS("SqsRequest", "QueueUrl", "QueueName"),
  Kinesis("KinesisRequest", "StreamName"),
  DynamoDB("DynamoDbRequest", "TableName");

  private final String requestClass;
  private final String[] fields;

  RequestType(String requestClass, String... fields) {
    this.requestClass = requestClass;
    this.fields = fields;
  }

  String[] getFields() {
    return fields;
  }

  @Nullable
  static RequestType ofSdkRequest(SdkRequest request) {
    // exact request class should be 1st level child of request type
    String typeName =
        (request.getClass().getSuperclass() == null
                ? request.getClass()
                : request.getClass().getSuperclass())
            .getSimpleName();
    for (RequestType type : values()) {
      if (type.requestClass.equals(typeName)) {
        return type;
      }
    }
    return null;
  }
}
