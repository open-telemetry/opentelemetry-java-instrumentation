/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;

public abstract class TracingSQSEventHandler extends TracingRequestHandler<SQSEvent, Void> {

  private final AwsLambdaMessageTracer tracer;

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSQSEventHandler() {
    this.tracer = new AwsLambdaMessageTracer();
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSQSEventHandler(Tracer tracer) {
    super(tracer);
    this.tracer = new AwsLambdaMessageTracer(tracer);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaMessageTracer}.
   */
  protected TracingSQSEventHandler(AwsLambdaMessageTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Void doHandleRequest(SQSEvent event, Context context) {
    Span span = tracer.startSpan(context, event);
    Throwable error = null;
    try (Scope ignored = tracer.startScope(span)) {
      handleEvent(event, context);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(span, error);
      } else {
        tracer.end(span);
      }
      OpenTelemetrySdk.getGlobalTracerManagement().forceFlush().join(1, TimeUnit.SECONDS);
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
