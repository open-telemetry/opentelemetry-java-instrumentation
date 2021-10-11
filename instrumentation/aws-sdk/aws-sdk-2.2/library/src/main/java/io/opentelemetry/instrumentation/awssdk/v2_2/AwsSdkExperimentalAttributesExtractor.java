/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.COMPONENT_NAME;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkExperimentalAttributesExtractor
    extends AttributesExtractor<ExecutionAttributes, SdkHttpResponse> {
  @Override
  protected void onStart(AttributesBuilder attributes, ExecutionAttributes executionAttributes) {
    String awsServiceName = executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    attributes.put("aws.agent", COMPONENT_NAME);
    attributes.put("aws.service", awsServiceName);
    attributes.put("aws.operation", awsOperation);
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      ExecutionAttributes executionAttributes,
      @Nullable SdkHttpResponse sdkHttpResponse,
      @Nullable Throwable error) {}
}
