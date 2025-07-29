/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_LAMBDA_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsExperimentalAttributes.AWS_LAMBDA_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.internal.FieldMapping.response;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(request("aws.bucket.name", "Bucket")),
  SQS(request("aws.queue.url", "QueueUrl"), request("aws.queue.name", "QueueName")),
  KINESIS(request("aws.stream.name", "StreamName")),
  DYNAMODB(request("aws.table.name", "TableName")),
  BEDROCK_RUNTIME(),
  LAMBDA(
      request(AWS_LAMBDA_NAME.getKey(), "FunctionName"),
      request(AttributeKeys.AWS_LAMBDA_RESOURCE_MAPPING_ID.getKey(), "UUID"),
      response(AWS_LAMBDA_ARN.getKey(), "Configuration.FunctionArn"),
      response(AttributeKeys.AWS_LAMBDA_RESOURCE_MAPPING_ID.getKey(), "UUID")),
  SECRETSMANAGER(response(AttributeKeys.AWS_SECRETSMANAGER_SECRET_ARN.getKey(), "ARN")),
  SNS(
      /*
       * Only one of TopicArn and TargetArn are permitted on an SNS request.
       */
      request(AttributeKeys.MESSAGING_DESTINATION_NAME.getKey(), "TargetArn"),
      request(AttributeKeys.MESSAGING_DESTINATION_NAME.getKey(), "TopicArn"),
      request(AttributeKeys.AWS_SNS_TOPIC_ARN.getKey(), "TopicArn"),
      response(AttributeKeys.AWS_SNS_TOPIC_ARN.getKey(), "TopicArn")),
  STEPFUNCTIONS(
      request(AttributeKeys.AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN.getKey(), "stateMachineArn"),
      request(AttributeKeys.AWS_STEP_FUNCTIONS_ACTIVITY_ARN.getKey(), "activityArn"));

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

    // copied from MessagingIncubatingAttributes
    static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
        AttributeKey.stringKey("messaging.destination.name");
  }
}
