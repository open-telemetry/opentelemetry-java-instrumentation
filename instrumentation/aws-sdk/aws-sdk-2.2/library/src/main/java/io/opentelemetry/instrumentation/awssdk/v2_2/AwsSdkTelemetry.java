/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpResponse;

/**
 * Entrypoint to OpenTelemetry instrumentation of the AWS SDK. Register the {@link
 * ExecutionInterceptor} returned by {@link #newExecutionInterceptor()} with an SDK client to have
 * all requests traced.
 *
 * <pre>{@code
 * DynamoDbClient dynamoDb = DynamoDbClient.builder()
 *     .overrideConfiguration(ClientOverrideConfiguration.builder()
 *         .addExecutionInterceptor(AwsSdkTelemetry.create(openTelemetry).newExecutionInterceptor())
 *         .build())
 *     .build();
 * }</pre>
 */
public class AwsSdkTelemetry {

  /** Returns a new {@link AwsSdkTelemetry} configured with the given {@link OpenTelemetry}. */
  public static AwsSdkTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link AwsSdkTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static AwsSdkTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new AwsSdkTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ExecutionAttributes, SdkHttpResponse> tracer;
  private final boolean captureExperimentalSpanAttributes;

  AwsSdkTelemetry(OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    this.tracer =
        AwsSdkInstrumenterFactory.createInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes);
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  /**
   * Returns a new {@link ExecutionInterceptor} that can be used with methods like {@link
   * ClientOverrideConfiguration.Builder#addExecutionInterceptor(ExecutionInterceptor)}.
   */
  public ExecutionInterceptor newExecutionInterceptor() {
    return new TracingExecutionInterceptor(tracer, captureExperimentalSpanAttributes);
  }
}
