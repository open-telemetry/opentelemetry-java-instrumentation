/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/**
 * Entrypoint for tracing AWS SDK v1 clients.
 *
 * <p>AWS SDK v1 is quite old and has some known bugs that are not fixed due to possible backwards
 * compatibility issues. Notably, if a {@link RequestHandler2} throws an exception in a callback,
 * this exception will leak up the chain and prevent other handlers from being executed. You must
 * ensure you do not register any problematic {@link RequestHandler2}s on your clients or you will
 * witness broken traces.
 */
public class AwsSdkTracing {

  /**
   * Returns the OpenTelemetry {@link Context} stored in the {@link Request}, or {@code null} if
   * there is no {@link Context}. This is generally not needed unless you are implementing your own
   * instrumentation that delegates to this one.
   */
  public static Context getOpenTelemetryContext(Request<?> request) {
    return request.getHandlerContext(TracingRequestHandler.CONTEXT);
  }

  /** Returns a new {@link AwsSdkTracing} configured with the given {@link OpenTelemetry}. */
  public static AwsSdkTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link AwsSdkTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static AwsSdkTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new AwsSdkTracingBuilder(openTelemetry);
  }

  private final Instrumenter<Request<?>, Response<?>> requestInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> consumerInstrumenter;

  AwsSdkTracing(OpenTelemetry openTelemetry, boolean captureExperimentalSpanAttributes) {
    requestInstrumenter =
        AwsSdkInstrumenterFactory.requestInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes);
    consumerInstrumenter =
        AwsSdkInstrumenterFactory.consumerInstrumenter(
            openTelemetry, captureExperimentalSpanAttributes);
  }

  /**
   * Returns a {@link RequestHandler2} for registration to AWS SDK client builders using {@code
   * withRequestHandlers}.
   */
  public RequestHandler2 newRequestHandler() {
    return new TracingRequestHandler(requestInstrumenter, consumerInstrumenter);
  }
}
