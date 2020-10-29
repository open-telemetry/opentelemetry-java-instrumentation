/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.CONTEXT_ATTRIBUTE;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

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
      OpenTelemetry.getGlobalTracer(AwsSdkHttpClientTracer.TRACER.getInstrumentationName());

  /** Returns the {@link Tracer} used to instrument the AWS SDK. */
  public static Tracer tracer() {
    return tracer;
  }

  /**
   * Returns an {@link ExecutionInterceptor} that can be used with an {@link
   * software.amazon.awssdk.http.SdkHttpClient} to trace SDK requests. Spans are created with the
   * kind {@link Kind#CLIENT}. If you also instrument the HTTP calls made by the SDK, e.g., by
   * adding Apache HTTP client or Netty instrumentation, you may want to use {@link
   * #newInterceptor(Kind)} with {@link Kind#INTERNAL} instead.
   */
  public static ExecutionInterceptor newInterceptor() {
    return newInterceptor(Kind.CLIENT);
  }

  /**
   * Returns an {@link ExecutionInterceptor} that can be used with an {@link
   * software.amazon.awssdk.http.SdkHttpClient} to trace SDK requests. Spans are created with the
   * provided {@link Kind}.
   */
  public static ExecutionInterceptor newInterceptor(Kind kind) {
    return new TracingExecutionInterceptor(kind);
  }

  /**
   * Returns the {@link Span} stored in the {@link ExecutionAttributes}, or {@code null} if there is
   * no span set.
   */
  public static Span getSpanFromAttributes(ExecutionAttributes attributes) {
    Context context = getContextFromAttributes(attributes);
    return context == null ? Span.getInvalid() : Span.fromContext(context);
  }

  /**
   * Returns the {@link Span} stored in the {@link ExecutionAttributes}, or {@code null} if there is
   * no span set.
   */
  public static Context getContextFromAttributes(ExecutionAttributes attributes) {
    return attributes.getAttribute(CONTEXT_ATTRIBUTE);
  }
}
