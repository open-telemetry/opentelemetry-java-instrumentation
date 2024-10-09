/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

final class AwsExperimentalAttributes {
  static final AttributeKey<String> AWS_AGENT = stringKey("aws.agent");
  static final AttributeKey<String> AWS_ENDPOINT = stringKey("aws.endpoint");
  static final AttributeKey<String> AWS_BUCKET_NAME = stringKey("aws.bucket.name");
  static final AttributeKey<String> AWS_QUEUE_URL = stringKey("aws.queue.url");
  static final AttributeKey<String> AWS_QUEUE_NAME = stringKey("aws.queue.name");
  static final AttributeKey<String> AWS_STREAM_NAME = stringKey("aws.stream.name");
  static final AttributeKey<String> AWS_TABLE_NAME = stringKey("aws.table.name");
  static final AttributeKey<String> AWS_REQUEST_ID = stringKey("aws.requestId");
  static final AttributeKey<String> AWS_AGENT_ID = stringKey("aws.bedrock.agent.id");
  static final AttributeKey<String> AWS_KNOWLEDGE_BASE_ID =
      stringKey("aws.bedrock.knowledge_base.id");
  static final AttributeKey<String> AWS_DATA_SOURCE_ID = stringKey("aws.bedrock.data_source.id");
  static final AttributeKey<String> AWS_GUARDRAIL_ID = stringKey("aws.bedrock.guardrail.id");
  static final AttributeKey<String> AWS_GUARDRAIL_ARN = stringKey("aws.bedrock.guardrail.arn");

  // TODO: Merge in gen_ai attributes in opentelemetry-semconv-incubating once upgrade to v1.26.0
  static final AttributeKey<String> AWS_BEDROCK_RUNTIME_MODEL_ID =
      stringKey("gen_ai.request.model");
  static final AttributeKey<String> AWS_BEDROCK_SYSTEM = stringKey("gen_ai.system");

  static final AttributeKey<String> AWS_STATE_MACHINE_ARN =
      stringKey("aws.stepfunctions.state_machine.arn");

  static final AttributeKey<String> AWS_STEP_FUNCTIONS_ACTIVITY_ARN =
      stringKey("aws.stepfunctions.activity.arn");

  static final AttributeKey<String> AWS_SNS_TOPIC_ARN = stringKey("aws.sns.topic.arn");

  static final AttributeKey<String> AWS_SECRET_ARN = stringKey("aws.secretsmanager.secret.arn");

  static final AttributeKey<String> AWS_LAMBDA_NAME = stringKey("aws.lambda.function.name");

  static final AttributeKey<String> AWS_LAMBDA_RESOURCE_ID =
      stringKey("aws.lambda.resource_mapping.id");

  private AwsExperimentalAttributes() {}
}
