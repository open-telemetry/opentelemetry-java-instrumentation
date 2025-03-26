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
import java.util.List;

/**
 * Entrypoint for instrumenting AWS SDK v1 clients.
 *
 * <p>AWS SDK v1 is quite old and has some known bugs that are not fixed due to possible backwards
 * compatibility issues. Notably, if a {@link RequestHandler2} throws an exception in a callback,
 * this exception will leak up the chain and prevent other handlers from being executed. You must
 * ensure you do not register any problematic {@link RequestHandler2}s on your clients or you will
 * witness broken traces.
 */
public class AwsSdkTelemetry {

  /**
   * Returns the OpenTelemetry {@link Context} stored in the {@link Request}, or {@code null} if
   * there is no {@link Context}. This is generally not needed unless you are implementing your own
   * instrumentation that delegates to this one.
   */
  public static Context getOpenTelemetryContext(Request<?> request) {
    return request.getHandlerContext(TracingRequestHandler.CONTEXT);
  }

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

  private final Instrumenter<Request<?>, Response<?>> requestInstrumenter;
  private final Instrumenter<SqsReceiveRequest, Response<?>> consumerReceiveInstrumenter;
  private final Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> producerInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> dynamoDbInstrumenter;

  AwsSdkTelemetry(
      OpenTelemetry openTelemetry,
      List<String> capturedHeaders,
      boolean captureExperimentalSpanAttributes,
      boolean messagingReceiveInstrumentationEnabled) {
    AwsSdkInstrumenterFactory instrumenterFactory =
        new AwsSdkInstrumenterFactory(
            openTelemetry,
            capturedHeaders,
            captureExperimentalSpanAttributes,
            messagingReceiveInstrumentationEnabled);
    requestInstrumenter = instrumenterFactory.requestInstrumenter();
    consumerReceiveInstrumenter = instrumenterFactory.consumerReceiveInstrumenter();
    consumerProcessInstrumenter = instrumenterFactory.consumerProcessInstrumenter();
    producerInstrumenter = instrumenterFactory.producerInstrumenter();
    dynamoDbInstrumenter = instrumenterFactory.dynamoDbInstrumenter();
  }

  /**
   * Returns a {@link RequestHandler2} for registration to AWS SDK client builders using {@code
   * withRequestHandlers}.
   */
  public RequestHandler2 newRequestHandler() {
    return new TracingRequestHandler(
        requestInstrumenter,
        consumerReceiveInstrumenter,
        consumerProcessInstrumenter,
        producerInstrumenter,
        dynamoDbInstrumenter);
  }
}
