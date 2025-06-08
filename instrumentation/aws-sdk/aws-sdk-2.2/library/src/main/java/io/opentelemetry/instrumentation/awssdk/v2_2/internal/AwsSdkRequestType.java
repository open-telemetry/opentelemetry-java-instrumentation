/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_SECRET_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_TABLE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.response;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(request(AWS_BUCKET_NAME.getKey(), "Bucket")),
  SQS(request(AWS_QUEUE_URL.getKey(), "QueueUrl"), request(AWS_QUEUE_NAME.getKey(), "QueueName")),
  KINESIS(request(AWS_STREAM_NAME.getKey(), "StreamName")),
  DYNAMODB(request(AWS_TABLE_NAME.getKey(), "TableName")),
  BEDROCK_RUNTIME(),
  SECRETSMANAGER(response(AWS_SECRET_ARN.getKey(), "ARN")),
  SNS(
      /*
       * Only one of TopicArn and TargetArn are permitted on an SNS request.
       */
      request(AttributeKeys.MESSAGING_DESTINATION_NAME.getKey(), "TargetArn"),
      request(AttributeKeys.MESSAGING_DESTINATION_NAME.getKey(), "TopicArn"));

  // Wrapping in unmodifiableMap
  @SuppressWarnings("ImmutableEnumChecker")
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fields = Collections.unmodifiableMap(FieldMapping.groupByType(fieldMappings));
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }

  private static class AttributeKeys {
    // copied from MessagingIncubatingAttributes
    static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
        AttributeKey.stringKey("messaging.destination.name");
  }
}
