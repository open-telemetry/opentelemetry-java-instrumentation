/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.TracingExecutionInterceptor.SPAN_ATTRIBUTE;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
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

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.aws-sdk-2.2");

  /** Returns the {@link Tracer} used to instrument the AWS SDK. */
  public static Tracer tracer() {
    return TRACER;
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
    return attributes.getAttribute(SPAN_ATTRIBUTE);
  }
}
