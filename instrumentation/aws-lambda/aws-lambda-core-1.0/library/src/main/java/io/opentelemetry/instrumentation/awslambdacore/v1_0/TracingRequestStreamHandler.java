/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.ApiGatewayProxyRequest;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenter;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.AwsLambdaFunctionInstrumenterFactory;
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
  private final AwsLambdaFunctionInstrumenter instrumenter;

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
    this(
        openTelemetrySdk,
        flushTimeout,
        AwsLambdaFunctionInstrumenterFactory.createInstrumenter(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingRequestStreamHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link AwsLambdaFunctionInstrumenter}.
   */
  protected TracingRequestStreamHandler(
      OpenTelemetrySdk openTelemetrySdk,
      Duration flushTimeout,
      AwsLambdaFunctionInstrumenter instrumenter) {
    this.openTelemetrySdk = openTelemetrySdk;
    this.flushTimeoutNanos = flushTimeout.toNanos();
    this.instrumenter = instrumenter;
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    ApiGatewayProxyRequest proxyRequest = ApiGatewayProxyRequest.forStream(input);
    AwsLambdaRequest request = createRequest(input, context, proxyRequest);
    io.opentelemetry.context.Context parentContext = instrumenter.extract(request);

    if (!instrumenter.shouldStart(parentContext, request)) {
      doHandleRequest(proxyRequest.freshStream(), output, context, request);
      return;
    }

    io.opentelemetry.context.Context otelContext = instrumenter.start(parentContext, request);
    Throwable error = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      doHandleRequest(
          proxyRequest.freshStream(),
          new OutputStreamWrapper(output, otelContext),
          context,
          request);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      instrumenter.end(otelContext, request, null, error);
      LambdaUtils.forceFlush(openTelemetrySdk, flushTimeoutNanos, TimeUnit.NANOSECONDS);
    }
  }

  protected AwsLambdaRequest createRequest(
      InputStream input, Context context, ApiGatewayProxyRequest proxyRequest) throws IOException {
    return AwsLambdaRequest.create(context, proxyRequest, proxyRequest.getHeaders());
  }

  protected void doHandleRequest(
      InputStream input, OutputStream output, Context context, AwsLambdaRequest request)
      throws IOException {
    doHandleRequest(input, output, context);
  }

  protected abstract void doHandleRequest(InputStream input, OutputStream output, Context context)
      throws IOException;

  private static class OutputStreamWrapper extends OutputStream {

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
      Span span = Span.fromContext(otelContext);
      span.addEvent("Output stream closed");
    }
  }
}
