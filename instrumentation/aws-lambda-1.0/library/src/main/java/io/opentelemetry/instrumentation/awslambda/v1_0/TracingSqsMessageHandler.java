/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public abstract class TracingSqsMessageHandler extends TracingSqsEventHandler {

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSqsMessageHandler() {
    super(new AwsLambdaMessageTracer());
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSqsMessageHandler(Tracer tracer) {
    super(new AwsLambdaMessageTracer(tracer));
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaMessageTracer}.
   */
  protected TracingSqsMessageHandler(AwsLambdaMessageTracer tracer) {
    super(tracer);
  }

  @Override
  protected final void handleEvent(SQSEvent event, Context context) {
    for (SQSMessage message : event.getRecords()) {
      Span span = getTracer().startSpan(message);
      Throwable error = null;
      try (Scope ignored = getTracer().startScope(span)) {
        handleMessage(message, context);
      } catch (Throwable t) {
        error = t;
        throw t;
      } finally {
        if (error != null) {
          getTracer().endExceptionally(span, error);
        } else {
          getTracer().end(span);
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
