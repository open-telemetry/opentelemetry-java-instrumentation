/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTracing;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context.AfterExecution;
import software.amazon.awssdk.core.interceptor.Context.AfterMarshalling;
import software.amazon.awssdk.core.interceptor.Context.AfterTransmission;
import software.amazon.awssdk.core.interceptor.Context.AfterUnmarshalling;
import software.amazon.awssdk.core.interceptor.Context.BeforeExecution;
import software.amazon.awssdk.core.interceptor.Context.BeforeMarshalling;
import software.amazon.awssdk.core.interceptor.Context.BeforeTransmission;
import software.amazon.awssdk.core.interceptor.Context.BeforeUnmarshalling;
import software.amazon.awssdk.core.interceptor.Context.FailedExecution;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpRequest;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpResponse;
import software.amazon.awssdk.core.interceptor.Context.ModifyRequest;
import software.amazon.awssdk.core.interceptor.Context.ModifyResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * {@link ExecutionInterceptor} that delegates to {@link AwsSdkTracing}, augmenting {@link
 * #beforeTransmission(BeforeTransmission, ExecutionAttributes)} to make sure the span is set to the
 * current context to allow downstream instrumentation like Netty to pick it up.
 */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  private final ExecutionInterceptor delegate;

  public TracingExecutionInterceptor() {
    delegate =
        AwsSdkTracing.newBuilder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(
                Config.get()
                    .getBooleanProperty(
                        "otel.instrumentation.aws-sdk.experimental-span-attributes", false))
            .build()
            .newExecutionInterceptor();
  }

  @Override
  public void beforeExecution(BeforeExecution context, ExecutionAttributes executionAttributes) {
    delegate.beforeExecution(context, executionAttributes);
  }

  @Override
  public SdkRequest modifyRequest(ModifyRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyRequest(context, executionAttributes);
  }

  @Override
  public void beforeMarshalling(
      BeforeMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeMarshalling(context, executionAttributes);
  }

  @Override
  public void afterMarshalling(AfterMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterMarshalling(context, executionAttributes);
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpRequest(context, executionAttributes);
  }

  @Override
  public Optional<RequestBody> modifyHttpContent(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpContent(context, executionAttributes);
  }

  @Override
  public Optional<AsyncRequestBody> modifyAsyncHttpContent(
      ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpContent(context, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      BeforeTransmission context, ExecutionAttributes executionAttributes) {
    delegate.beforeTransmission(context, executionAttributes);
  }

  @Override
  public void afterTransmission(
      AfterTransmission context, ExecutionAttributes executionAttributes) {
    delegate.afterTransmission(context, executionAttributes);
  }

  @Override
  public SdkHttpResponse modifyHttpResponse(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponse(context, executionAttributes);
  }

  @Override
  public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpResponseContent(context, executionAttributes);
  }

  @Override
  public Optional<InputStream> modifyHttpResponseContent(
      ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponseContent(context, executionAttributes);
  }

  @Override
  public void beforeUnmarshalling(
      BeforeUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeUnmarshalling(context, executionAttributes);
  }

  @Override
  public void afterUnmarshalling(
      AfterUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterUnmarshalling(context, executionAttributes);
  }

  @Override
  public SdkResponse modifyResponse(
      ModifyResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyResponse(context, executionAttributes);
  }

  @Override
  public void afterExecution(AfterExecution context, ExecutionAttributes executionAttributes) {
    delegate.afterExecution(context, executionAttributes);
  }

  @Override
  public Throwable modifyException(
      FailedExecution context, ExecutionAttributes executionAttributes) {
    return delegate.modifyException(context, executionAttributes);
  }

  @Override
  public void onExecutionFailure(FailedExecution context, ExecutionAttributes executionAttributes) {
    delegate.onExecutionFailure(context, executionAttributes);
  }
}
