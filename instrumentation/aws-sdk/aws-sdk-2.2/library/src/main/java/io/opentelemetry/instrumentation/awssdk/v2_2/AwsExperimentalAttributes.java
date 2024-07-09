/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

final class AwsExperimentalAttributes {
  static final AttributeKey<String> AWS_BUCKET_NAME = stringKey("aws.bucket.name");
  static final AttributeKey<String> AWS_QUEUE_URL = stringKey("aws.queue.url");
  static final AttributeKey<String> AWS_QUEUE_NAME = stringKey("aws.queue.name");
  static final AttributeKey<String> AWS_STREAM_NAME = stringKey("aws.stream.name");
  static final AttributeKey<String> AWS_TABLE_NAME = stringKey("aws.table.name");
  static final AttributeKey<String> AWS_BEDROCK_GUARDRAIL_ID =
      stringKey("aws.bedrock.guardrail.id");
  static final AttributeKey<String> AWS_BEDROCK_AGENT_ID = stringKey("aws.bedrock.agent.id");
  static final AttributeKey<String> AWS_BEDROCK_DATASOURCE_ID =
      stringKey("aws.bedrock.data_source.id");
  static final AttributeKey<String> AWS_BEDROCK_KNOWLEDGEBASE_ID =
      stringKey("aws.bedrock.knowledge_base.id");

  // TODO: Merge in gen_ai attributes in opentelemetry-semconv-incubating once upgrade to v1.26.0
  static final AttributeKey<String> GEN_AI_MODEL = stringKey("gen_ai.request.model");
  static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");

  private AwsExperimentalAttributes() {}
}
