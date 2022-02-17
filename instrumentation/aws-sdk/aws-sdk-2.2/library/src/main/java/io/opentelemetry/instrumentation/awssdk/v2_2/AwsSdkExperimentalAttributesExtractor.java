/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<ExecutionAttributes, SdkHttpResponse> {

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
      @Nullable SdkHttpResponse sdkHttpResponse,
      @Nullable Throwable error) {}
}
