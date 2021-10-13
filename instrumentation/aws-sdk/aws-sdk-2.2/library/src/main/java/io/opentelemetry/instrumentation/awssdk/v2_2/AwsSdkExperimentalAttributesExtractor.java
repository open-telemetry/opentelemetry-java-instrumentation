/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkExperimentalAttributesExtractor
    implements AttributesExtractor<ExecutionAttributes, SdkHttpResponse> {

  private static final String COMPONENT_NAME = "java-aws-sdk";
  private static final AttributeKey<String> AWS_AGENT = AttributeKey.stringKey("aws.agent");
  private static final AttributeKey<String> AWS_SERVICE = AttributeKey.stringKey("aws.service");
  private static final AttributeKey<String> AWS_OPERATION = AttributeKey.stringKey("aws.operation");

  @Override
  public void onStart(AttributesBuilder attributes, ExecutionAttributes executionAttributes) {
    String awsServiceName = executionAttributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = executionAttributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    attributes.put(AWS_AGENT, COMPONENT_NAME);
    attributes.put(AWS_SERVICE, awsServiceName);
    attributes.put(AWS_OPERATION, awsOperation);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      ExecutionAttributes executionAttributes,
      @Nullable SdkHttpResponse sdkHttpResponse,
      @Nullable Throwable error) {}
}
