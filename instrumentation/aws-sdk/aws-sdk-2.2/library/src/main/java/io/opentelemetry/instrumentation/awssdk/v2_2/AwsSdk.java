/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.OPERATION_ATTRIBUTE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.tracer.LazyHttpClientOperation;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * Entrypoint to OpenTelemetry instrumentation of the AWS SDK. Register the {@link
 * ExecutionInterceptor} returned by {@link #newInterceptor()} with an SDK client to have all
 * requests traced.
 *
 * <pre>{@code
 * DynamoDbClient dynamoDb = DynamoDbClient.builder()
 *     .overrideConfiguration(ClientOverrideConfiguration.builder()
 *         .addExecutionInterceptor(AwsSdk.newInterceptor())
 *         .build())
 *     .build();
 * }</pre>
 */
public class AwsSdk {

  private static final Tracer tracer =
      OpenTelemetry.getGlobalTracer(AwsSdkHttpClientTracer.tracer().getInstrumentationName());

  /** Returns the {@link Tracer} used to instrument the AWS SDK. */
  public static Tracer tracer() {
    return tracer;
  }

  /**
   * Returns an {@link ExecutionInterceptor} that can be used with an {@link
   * software.amazon.awssdk.http.SdkHttpClient} to trace SDK requests.
   */
  public static ExecutionInterceptor newInterceptor() {
    return new TracingExecutionInterceptor();
  }

  /**
   * Returns the {@link LazyHttpClientOperation} stored in the {@link ExecutionAttributes}, or
   * {@code null} if there is no operation set.
   */
  public static LazyHttpClientOperation<SdkHttpRequest, SdkHttpRequest.Builder, SdkHttpResponse>
      getOperationOrNoop(ExecutionAttributes attributes) {
    return orDefault(attributes.getAttribute(OPERATION_ATTRIBUTE), LazyHttpClientOperation.noop());
  }

  private static <T> T orDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
