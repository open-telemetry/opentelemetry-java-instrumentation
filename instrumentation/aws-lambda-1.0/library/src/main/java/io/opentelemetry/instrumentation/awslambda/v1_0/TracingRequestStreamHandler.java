/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * A base class similar to {@link RequestStreamHandler} but will automatically trace invocations of
 * {@link #doHandleRequest(InputStream input, OutputStream output, Context)}.
 */
public abstract class TracingRequestStreamHandler implements RequestStreamHandler {

  private final AwsLambdaTracer tracer;

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the default {@link
   * Tracer}.
   */
  protected TracingRequestStreamHandler() {
    this.tracer = new AwsLambdaTracer();
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the specified {@link
   * Tracer}.
   */
  protected TracingRequestStreamHandler(Tracer tracer) {
    this.tracer = new AwsLambdaTracer(tracer);
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the specified {@link
   * AwsLambdaTracer}.
   */
  protected TracingRequestStreamHandler(AwsLambdaTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public final void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    Span span = tracer.startSpan(context, Kind.SERVER);
    Throwable error = null;
    try (Scope ignored = tracer.startScope(span)) {
      doHandleRequest(input, output, context);
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

  protected abstract void doHandleRequest(InputStream input, OutputStream output, Context context)
      throws IOException;
}
