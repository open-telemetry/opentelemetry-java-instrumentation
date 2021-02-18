/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;

public abstract class TracingSqsEventHandler extends TracingRequestHandler<SQSEvent, Void> {

  private final AwsLambdaMessageTracer tracer;

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
    this(openTelemetrySdk, flushTimeout, new AwsLambdaMessageTracer(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingSqsEventHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link AwsLambdaTracer}.
   */
  protected TracingSqsEventHandler(
      OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout, AwsLambdaMessageTracer tracer) {
    super(openTelemetrySdk, flushTimeout);
    this.tracer = tracer;
  }

  @Override
  public Void doHandleRequest(SQSEvent event, Context context) {
    io.opentelemetry.context.Context otelContext = tracer.startSpan(event);
    Throwable error = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      handleEvent(event, context);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(otelContext, error);
      } else {
        tracer.end(otelContext);
      }
    }
    return null;
  }

  /**
   * Handles a {@linkplain SQSEvent batch of messages}. Implement this class to do the actual
   * processing of incoming SQS messages.
   */
  protected abstract void handleEvent(SQSEvent event, Context context);

  // We use in SQS message handler too.
  AwsLambdaMessageTracer getTracer() {
    return tracer;
  }
}
