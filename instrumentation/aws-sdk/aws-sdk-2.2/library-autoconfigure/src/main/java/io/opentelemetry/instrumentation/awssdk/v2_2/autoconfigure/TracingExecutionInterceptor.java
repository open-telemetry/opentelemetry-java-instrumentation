/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

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
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * A {@link ExecutionInterceptor} for use as an SPI by the AWS SDK to automatically trace all
 * requests.
 */
public class TracingExecutionInterceptor implements ExecutionInterceptor {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get().getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes", false);

  private final ExecutionInterceptor delegate =
      AwsSdkTracing.builder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES)
          .build()
          .newExecutionInterceptor();

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    delegate.beforeExecution(context, executionAttributes);
  }

  @Override
  public SdkRequest modifyRequest(
      Context.ModifyRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyRequest(context, executionAttributes);
  }

  @Override
  public void beforeMarshalling(
      Context.BeforeMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeMarshalling(context, executionAttributes);
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterMarshalling(context, executionAttributes);
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpRequest(context, executionAttributes);
  }

  @Override
  public Optional<RequestBody> modifyHttpContent(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpContent(context, executionAttributes);
  }

  @Override
  public Optional<AsyncRequestBody> modifyAsyncHttpContent(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpContent(context, executionAttributes);
  }

  @Override
  public void beforeTransmission(
      Context.BeforeTransmission context, ExecutionAttributes executionAttributes) {
    delegate.beforeTransmission(context, executionAttributes);
  }

  @Override
  public void afterTransmission(
      Context.AfterTransmission context, ExecutionAttributes executionAttributes) {
    delegate.afterTransmission(context, executionAttributes);
  }

  @Override
  public SdkHttpResponse modifyHttpResponse(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponse(context, executionAttributes);
  }

  @Override
  public Optional<Publisher<ByteBuffer>> modifyAsyncHttpResponseContent(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyAsyncHttpResponseContent(context, executionAttributes);
  }

  @Override
  public Optional<InputStream> modifyHttpResponseContent(
      Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyHttpResponseContent(context, executionAttributes);
  }

  @Override
  public void beforeUnmarshalling(
      Context.BeforeUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.beforeUnmarshalling(context, executionAttributes);
  }

  @Override
  public void afterUnmarshalling(
      Context.AfterUnmarshalling context, ExecutionAttributes executionAttributes) {
    delegate.afterUnmarshalling(context, executionAttributes);
  }

  @Override
  public SdkResponse modifyResponse(
      Context.ModifyResponse context, ExecutionAttributes executionAttributes) {
    return delegate.modifyResponse(context, executionAttributes);
  }

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    delegate.afterExecution(context, executionAttributes);
  }

  @Override
  public Throwable modifyException(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    return delegate.modifyException(context, executionAttributes);
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    delegate.onExecutionFailure(context, executionAttributes);
  }
}
