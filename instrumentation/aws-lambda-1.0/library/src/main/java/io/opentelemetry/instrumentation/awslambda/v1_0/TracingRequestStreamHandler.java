/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A base class similar to {@link RequestStreamHandler} but will automatically trace invocations of
 * {@link #doHandleRequest(InputStream input, OutputStream output, Context)}.
 */
public abstract class TracingRequestStreamHandler implements RequestStreamHandler {

  private static final long DEFAULT_FLUSH_TIMEOUT_SECONDS = 1;

  private final AwsLambdaTracer tracer;
  private final long flushTimeout;

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the default {@link
   * Tracer}.
   */
  protected TracingRequestStreamHandler(long flushTimeout) {
    this.tracer = new AwsLambdaTracer();
    this.flushTimeout = flushTimeout;
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the default {@link
   * Tracer}.
   */
  protected TracingRequestStreamHandler() {
    this.tracer = new AwsLambdaTracer();
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the specified {@link
   * Tracer}.
   */
  protected TracingRequestStreamHandler(Tracer tracer) {
    this.tracer = new AwsLambdaTracer(tracer);
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which traces using the specified {@link
   * AwsLambdaTracer}.
   */
  protected TracingRequestStreamHandler(AwsLambdaTracer tracer) {
    this.tracer = tracer;
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {

    ApiGatewayProxyRequest proxyRequest = ApiGatewayProxyRequest.forStream(input);
    io.opentelemetry.context.Context otelContext =
        tracer.startSpan(context, SpanKind.SERVER, input, proxyRequest.getHeaders());

    try (Scope ignored = otelContext.makeCurrent()) {
      doHandleRequest(
          proxyRequest.freshStream(), new OutputStreamWrapper(output, otelContext), context);
    } catch (Throwable t) {
      tracer.endExceptionally(otelContext, t);
      LambdaUtils.forceFlush();
      throw t;
    }
  }

  protected abstract void doHandleRequest(InputStream input, OutputStream output, Context context)
      throws IOException;

  private class OutputStreamWrapper extends OutputStream {

    private final OutputStream delegate;
    private final io.opentelemetry.context.Context otelContext;

    private OutputStreamWrapper(
        OutputStream delegate, io.opentelemetry.context.Context otelContext) {
      this.delegate = delegate;
      this.otelContext = otelContext;
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
      LambdaUtils.forceFlush();
    }
  }
}
