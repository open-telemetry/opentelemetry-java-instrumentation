/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<ExecutionAttributes, Response> {

  private static final String COMPONENT_NAME = "java-aws-sdk";
  private static final AttributeKey<String> AWS_AGENT = AttributeKey.stringKey("aws.agent");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ExecutionAttributes executionAttributes) {
    attributes.put(AWS_AGENT, COMPONENT_NAME);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ExecutionAttributes executionAttributes,
      @Nullable Response response,
      @Nullable Throwable error) {}
}
