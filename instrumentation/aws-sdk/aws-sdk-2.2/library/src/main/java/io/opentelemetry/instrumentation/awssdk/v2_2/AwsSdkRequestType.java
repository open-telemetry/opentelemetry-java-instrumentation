/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_AGENT_ID;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_BUCKET_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_DATA_SOURCE_ID;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_GUARDRAIL_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_GUARDRAIL_ID;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_KNOWLEDGE_BASE_ID;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_LAMBDA_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_LAMBDA_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_LAMBDA_RESOURCE_ID;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_QUEUE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_QUEUE_URL;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_SECRET_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_STATE_MACHINE_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_STEP_FUNCTIONS_ACTIVITY_ARN;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_STREAM_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.AWS_TABLE_NAME;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsExperimentalAttributes.GEN_AI_MODEL;
import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.request;
import static io.opentelemetry.instrumentation.awssdk.v2_2.FieldMapping.response;

import java.util.Collections;
import java.util.List;
import java.util.Map;

enum AwsSdkRequestType {
  S3(request(AWS_BUCKET_NAME.getKey(), "Bucket")),
  SQS(request(AWS_QUEUE_URL.getKey(), "QueueUrl"), request(AWS_QUEUE_NAME.getKey(), "QueueName")),
  KINESIS(request(AWS_STREAM_NAME.getKey(), "StreamName")),
  DYNAMODB(request(AWS_TABLE_NAME.getKey(), "TableName")),
  BEDROCK(
      request(AWS_GUARDRAIL_ID.getKey(), "guardrailIdentifier"),
      response(AWS_GUARDRAIL_ARN.getKey(), "guardrailArn")),
  BEDROCKAGENTOPERATION(
      request(AWS_AGENT_ID.getKey(), "agentId"),
      response(AWS_AGENT_ID.getKey(), "agentId"),
      request(AWS_KNOWLEDGE_BASE_ID.getKey(), "knowledgeBaseId"),
      response(AWS_KNOWLEDGE_BASE_ID.getKey(), "knowledgeBaseId")),
  BEDROCKDATASOURCEOPERATION(
      request(AWS_DATA_SOURCE_ID.getKey(), "dataSourceId"),
      response(AWS_DATA_SOURCE_ID.getKey(), "dataSourceId")),
  BEDROCKKNOWLEDGEBASEOPERATION(
      request(AWS_KNOWLEDGE_BASE_ID.getKey(), "knowledgeBaseId"),
      response(AWS_KNOWLEDGE_BASE_ID.getKey(), "knowledgeBaseId")),
  BEDROCKRUNTIME(request(GEN_AI_MODEL.getKey(), "modelId")),
  STEPFUNCTION(
      request(AWS_STATE_MACHINE_ARN.getKey(), "stateMachineArn"),
      request(AWS_STEP_FUNCTIONS_ACTIVITY_ARN.getKey(), "activityArn")),
  SNS(request(AWS_SNS_TOPIC_ARN.getKey(), "TopicArn")),
  SECRETSMANAGER(response(AWS_SECRET_ARN.getKey(), "ARN")),
  LAMBDA(
      request(AWS_LAMBDA_NAME.getKey(), "FunctionName"),
      request(AWS_LAMBDA_RESOURCE_ID.getKey(), "UUID"),
      response(AWS_LAMBDA_ARN.getKey(), "Configuration.FunctionArn"));

  // Wrapping in unmodifiableMap
  @SuppressWarnings("ImmutableEnumChecker")
  private final Map<FieldMapping.Type, List<FieldMapping>> fields;

  AwsSdkRequestType(FieldMapping... fieldMappings) {
    this.fields = Collections.unmodifiableMap(FieldMapping.groupByType(fieldMappings));
  }

  List<FieldMapping> fields(FieldMapping.Type type) {
    return fields.get(type);
  }
}
