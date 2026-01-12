/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

final class AwsExperimentalAttributes {
  // Work is underway to add these two keys to the SemConv AWS registry, in line with other AWS
  // resources.
  // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/registry/attributes/aws.md#amazon-lambda-attributes
  static final AttributeKey<String> AWS_LAMBDA_ARN = stringKey("aws.lambda.function.arn");
  static final AttributeKey<String> AWS_LAMBDA_NAME = stringKey("aws.lambda.function.name");

  private AwsExperimentalAttributes() {}
}
