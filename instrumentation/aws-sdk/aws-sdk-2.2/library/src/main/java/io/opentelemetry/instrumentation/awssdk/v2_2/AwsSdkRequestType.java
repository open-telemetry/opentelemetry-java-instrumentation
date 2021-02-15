/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.of;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

enum AwsSdkRequestType {
  S3(of("aws.bucket.name", "Bucket")),
  SQS(of("aws.queue.url", "QueueUrl"), of("aws.queue.name", "QueueName")),
  Kinesis(of("aws.stream.name", "StreamName")),
  DynamoDB(of("aws.table.name", "TableName"), of(SemanticAttributes.DB_NAME.getKey(), "TableName"));

  private final FieldMapping[] fieldMappings;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fieldMappings = fieldMappings;
  }

  public FieldMapping[] fields() {
    return fieldMappings;
  }
}
