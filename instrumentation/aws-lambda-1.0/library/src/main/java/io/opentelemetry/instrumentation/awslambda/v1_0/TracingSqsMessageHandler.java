/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;

public abstract class TracingSqsMessageHandler extends TracingSqsEventHandler {

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSqsMessageHandler(OpenTelemetrySdk openTelemetrySdk) {
    super(openTelemetrySdk);
  }

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSqsMessageHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    super(openTelemetrySdk, flushTimeout);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSqsMessageHandler(
      OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout, AwsLambdaMessageTracer tracer) {
    super(openTelemetrySdk, flushTimeout, tracer);
  }

  @Override
  protected final void handleEvent(SQSEvent event, Context context) {
    for (SQSMessage message : event.getRecords()) {
      io.opentelemetry.context.Context otelContext = getTracer().startSpan(message);
      Throwable error = null;
      try (Scope ignored = otelContext.makeCurrent()) {
        handleMessage(message, context);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        if (error != null) {
          getTracer().endExceptionally(otelContext, error);
        } else {
          getTracer().end(otelContext);
        }
      }
    }
  }

  /**
   * Handles a {@linkplain SQSMessage message}. Implement this class to do the actual processing of
   * incoming SQS messages.
   */
  protected abstract void handleMessage(SQSMessage message, Context context);
}
