/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.TimeUnit;

/**
 * A base class similar to {@link RequestHandler} but will automatically trace invocations of {@link
 * #doHandleRequest(Object, Context)}.
 */
public abstract class TracingRequestHandler<I, O> implements RequestHandler<I, O> {

  private final AwsLambdaTracer tracer;

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingRequestHandler() {
    this.tracer = new AwsLambdaTracer();
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingRequestHandler(Tracer tracer) {
    this.tracer = new AwsLambdaTracer(tracer);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaTracer}.
   */
  protected TracingRequestHandler(AwsLambdaTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public final O handleRequest(I input, Context context) {
    Span span = tracer.startSpan(context, Kind.SERVER);
    Throwable error = null;
    try (Scope ignored = tracer.startScope(span)) {
      return doHandleRequest(input, context);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(span, error);
      } else {
        tracer.end(span);
      }
      OpenTelemetrySdk.getTracerManagement().forceFlush().join(1, TimeUnit.SECONDS);
    }
  }

  protected abstract O doHandleRequest(I input, Context context);
}
