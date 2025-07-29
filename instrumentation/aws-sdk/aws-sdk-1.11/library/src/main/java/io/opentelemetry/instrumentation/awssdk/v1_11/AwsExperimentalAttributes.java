/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

final class AwsExperimentalAttributes {
  static final AttributeKey<String> AWS_AGENT = stringKey("aws.agent");
  static final AttributeKey<String> AWS_BUCKET_NAME = stringKey("aws.bucket.name");
  static final AttributeKey<String> AWS_QUEUE_URL = stringKey("aws.queue.url");
  static final AttributeKey<String> AWS_QUEUE_NAME = stringKey("aws.queue.name");
  static final AttributeKey<String> AWS_STREAM_NAME = stringKey("aws.stream.name");
  static final AttributeKey<String> AWS_TABLE_NAME = stringKey("aws.table.name");

  // Work is underway to add these two keys to the SemConv AWS registry, in line with other AWS
  // resources.
  // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/registry/attributes/aws.md#amazon-lambda-attributes
  static final AttributeKey<String> AWS_LAMBDA_ARN = stringKey("aws.lambda.function.arn");
  static final AttributeKey<String> AWS_LAMBDA_NAME = stringKey("aws.lambda.function.name");

  private AwsExperimentalAttributes() {}
}
