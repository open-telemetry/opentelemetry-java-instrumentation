/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_LAMBDA_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_LAMBDA_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.requestExperimental;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.response;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.responseExperimental;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(request(AttributeKeys.AWS_S3_BUCKET, "Bucket")),
  SQS(
      request(AttributeKeys.AWS_SQS_QUEUE_URL, "QueueUrl"),
      requestExperimental(AttributeKey.stringKey("aws.queue.name"), "QueueName")),
  KINESIS(request(AttributeKeys.AWS_KINESIS_STREAM_NAME, "StreamName")),
  DYNAMODB(),
  BEDROCK_RUNTIME(),
  LAMBDA(
      requestExperimental(AWS_LAMBDA_NAME, "FunctionName"),
      request(AttributeKeys.AWS_LAMBDA_RESOURCE_MAPPING_ID, "UUID"),
      responseExperimental(AWS_LAMBDA_ARN, "Configuration.FunctionArn"),
      response(AttributeKeys.AWS_LAMBDA_RESOURCE_MAPPING_ID, "UUID")),
  SECRETSMANAGER(response(AttributeKeys.AWS_SECRETSMANAGER_SECRET_ARN, "ARN")),
  SNS(
      /*
       * Only one of TopicArn and TargetArn are permitted on an SNS request.
       */
      request(AttributeKeys.MESSAGING_DESTINATION_NAME, "TargetArn"),
      request(AttributeKeys.MESSAGING_DESTINATION_NAME, "TopicArn"),
      request(AttributeKeys.AWS_SNS_TOPIC_ARN, "TopicArn"),
      response(AttributeKeys.AWS_SNS_TOPIC_ARN, "TopicArn")),
  STEP_FUNCTIONS(
      request(AttributeKeys.AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN, "stateMachineArn"),
      request(AttributeKeys.AWS_STEP_FUNCTIONS_ACTIVITY_ARN, "activityArn"));

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
    // Copied from AwsIncubatingAttributes
    private static final AttributeKey<String> AWS_LAMBDA_RESOURCE_MAPPING_ID =
        stringKey("aws.lambda.resource_mapping.id");
    static final AttributeKey<String> AWS_SECRETSMANAGER_SECRET_ARN =
        stringKey("aws.secretsmanager.secret.arn");
    static final AttributeKey<String> AWS_SNS_TOPIC_ARN = stringKey("aws.sns.topic.arn");
    static final AttributeKey<String> AWS_STEP_FUNCTIONS_ACTIVITY_ARN =
        stringKey("aws.step_functions.activity.arn");
    static final AttributeKey<String> AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN =
        stringKey("aws.step_functions.state_machine.arn");
    static final AttributeKey<String> AWS_S3_BUCKET = stringKey("aws.s3.bucket");
    static final AttributeKey<String> AWS_SQS_QUEUE_URL = stringKey("aws.sqs.queue.url");
    static final AttributeKey<String> AWS_KINESIS_STREAM_NAME =
        stringKey("aws.kinesis.stream_name");

    // copied from MessagingIncubatingAttributes
    static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
        AttributeKey.stringKey("messaging.destination.name");
  }
}
