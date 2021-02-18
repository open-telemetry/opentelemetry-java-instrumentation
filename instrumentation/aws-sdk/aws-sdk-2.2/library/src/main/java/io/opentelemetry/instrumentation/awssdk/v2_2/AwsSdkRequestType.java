/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.request;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(request("aws.bucket.name", "Bucket")),
  SQS(request("aws.queue.url", "QueueUrl"), request("aws.queue.name", "QueueName")),
  Kinesis(request("aws.stream.name", "StreamName")),
  DynamoDB(
      request("aws.table.name", "TableName"),
      request(SemanticAttributes.DB_NAME.getKey(), "TableName"));

  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fields = FieldMapping.groupByType(fieldMappings);
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }
}
