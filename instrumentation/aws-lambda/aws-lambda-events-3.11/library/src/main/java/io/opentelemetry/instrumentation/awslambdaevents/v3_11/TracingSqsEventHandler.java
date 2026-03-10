/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v3_11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.TracingRequestHandler;
import io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal.AwsLambdaSqsInstrumenterFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;
import javax.annotation.Nullable;

public abstract class TracingSqsEventHandler
    extends TracingRequestHandler<SQSEvent, SQSBatchResponse> {
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-lambda-events-3.11";

  private final Instrumenter<SQSEvent, Void> instrumenter;

  /**
   * Creates a new {@link TracingSqsEventHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of 1s when flushing at the end of an invocation.
   */
  protected TracingSqsEventHandler(OpenTelemetrySdk openTelemetrySdk) {
    this(openTelemetrySdk, DEFAULT_FLUSH_TIMEOUT);
  }

  /**
   * Creates a new {@link TracingSqsEventHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation.
   */
  protected TracingSqsEventHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    this(
        openTelemetrySdk,
        flushTimeout,
        AwsLambdaSqsInstrumenterFactory.forEvent(openTelemetrySdk, INSTRUMENTATION_NAME));
  }

  /**
   * Creates a new {@link TracingSqsEventHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link
   * io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter}.
   */
  protected TracingSqsEventHandler(
      OpenTelemetrySdk openTelemetrySdk,
      Duration flushTimeout,
      Instrumenter<SQSEvent, Void> instrumenter) {
    super(openTelemetrySdk, flushTimeout);
    this.instrumenter = instrumenter;
  }

  @Nullable
  @Override
  public SQSBatchResponse doHandleRequest(SQSEvent event, Context context) {
    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.current();
    if (instrumenter.shouldStart(parentContext, event)) {
      io.opentelemetry.context.Context otelContext = instrumenter.start(parentContext, event);
      Throwable error = null;
      try (Scope ignored = otelContext.makeCurrent()) {
        return handleEvent(event, context);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        instrumenter.end(otelContext, event, null, error);
      }
    } else {
      return handleEvent(event, context);
    }
  }

  /**
   * Handles a {@linkplain SQSEvent batch of messages}. Implement this class to do the actual
   * processing of incoming SQS messages.
   */
  @Nullable
  protected abstract SQSBatchResponse handleEvent(SQSEvent event, Context context);
}
