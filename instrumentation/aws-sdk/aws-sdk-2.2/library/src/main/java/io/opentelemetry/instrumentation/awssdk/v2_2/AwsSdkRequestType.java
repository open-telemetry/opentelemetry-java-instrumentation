/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.request;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

enum AwsSdkRequestType {
  S3(request("aws.bucket.name", "Bucket")),
  SQS(request("aws.queue.url", "QueueUrl"), request("aws.queue.name", "QueueName")),
  Kinesis(request("aws.stream.name", "StreamName")),
  DynamoDB(
      request("aws.table.name", "TableName"),
      request(SemanticAttributes.DB_NAME.getKey(), "TableName"));

  private final FieldMapping[] fieldMappings;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fieldMappings = fieldMappings;
  }

  public FieldMapping[] fields() {
    return fieldMappings;
  }
}
