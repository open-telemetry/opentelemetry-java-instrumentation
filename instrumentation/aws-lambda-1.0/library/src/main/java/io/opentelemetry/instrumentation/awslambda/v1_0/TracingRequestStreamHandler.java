/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A base class similar to {@link RequestStreamHandler} but will automatically trace invocations of
 * {@link #doHandleRequest(InputStream input, OutputStream output, Context)}.
 */
public abstract class TracingRequestStreamHandler implements RequestStreamHandler {

  private static final Duration DEFAULT_FLUSH_TIMEOUT = Duration.ofSeconds(1);

  private final OpenTelemetrySdk openTelemetrySdk;
  private final long flushTimeoutNanos;
  private final AwsLambdaTracer tracer;

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of 1s when flushing at the end of an invocation.
   */
  protected TracingRequestStreamHandler(OpenTelemetrySdk openTelemetrySdk) {
    this(openTelemetrySdk, DEFAULT_FLUSH_TIMEOUT);
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation.
   */
  protected TracingRequestStreamHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    this(openTelemetrySdk, flushTimeout, new AwsLambdaTracer(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link AwsLambdaTracer}.
   */
  protected TracingRequestStreamHandler(
      OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout, AwsLambdaTracer tracer) {
    this.openTelemetrySdk = openTelemetrySdk;
    this.flushTimeoutNanos = flushTimeout.toNanos();
    this.tracer = tracer;
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {

    ApiGatewayProxyRequest proxyRequest = ApiGatewayProxyRequest.forStream(input);
    io.opentelemetry.context.Context otelContext =
        tracer.startSpan(context, SpanKind.SERVER, input, proxyRequest.getHeaders());

    try (Scope ignored = otelContext.makeCurrent()) {
      doHandleRequest(
          proxyRequest.freshStream(),
          new OutputStreamWrapper(output, otelContext, openTelemetrySdk),
          context);
    } catch (Throwable t) {
      tracer.endExceptionally(otelContext, t);
      openTelemetrySdk
          .getSdkTracerProvider()
          .forceFlush()
          .join(flushTimeoutNanos, TimeUnit.NANOSECONDS);
      throw t;
    }
  }

  protected abstract void doHandleRequest(InputStream input, OutputStream output, Context context)
      throws IOException;

  private class OutputStreamWrapper extends OutputStream {

    private final OutputStream delegate;
    private final io.opentelemetry.context.Context otelContext;
    private final OpenTelemetrySdk openTelemetrySdk;

    private OutputStreamWrapper(
        OutputStream delegate,
        io.opentelemetry.context.Context otelContext,
        OpenTelemetrySdk openTelemetrySdk) {
      this.delegate = delegate;
      this.otelContext = otelContext;
      this.openTelemetrySdk = openTelemetrySdk;
    }

    @Override
    public void write(byte[] b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
      tracer.end(otelContext);
      openTelemetrySdk
          .getSdkTracerProvider()
          .forceFlush()
          .join(flushTimeoutNanos, TimeUnit.NANOSECONDS);
    }
  }
}
