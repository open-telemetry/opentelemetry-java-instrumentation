/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A base class similar to {@link RequestHandler} but will automatically trace invocations of {@link
 * #doHandleRequest(Object, Context)}. For API Gateway requests (ie of APIGatewayProxyRequestEvent
 * type parameter) also HTTP propagation can be enabled.
 */
public abstract class TracingRequestHandler<I, O> implements RequestHandler<I, O> {

  protected static final Duration DEFAULT_FLUSH_TIMEOUT = Duration.ofSeconds(1);

  private final AwsLambdaTracer tracer;
  private final OpenTelemetrySdk openTelemetrySdk;
  private final long flushTimeoutNanos;

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of 1s when flushing at the end of an invocation.
   */
  protected TracingRequestHandler(OpenTelemetrySdk openTelemetrySdk) {
    this(openTelemetrySdk, DEFAULT_FLUSH_TIMEOUT);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the provided {@link
   * OpenTelemetrySdk} and has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation.
   */
  protected TracingRequestHandler(OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout) {
    this(openTelemetrySdk, flushTimeout, new AwsLambdaTracer(openTelemetrySdk));
  }

  /**
   * Creates a new {@link TracingRequestHandler} which flushes the provided {@link
   * OpenTelemetrySdk}, has a timeout of {@code flushTimeout} when flushing at the end of an
   * invocation, and traces using the provided {@link AwsLambdaTracer}.
   */
  protected TracingRequestHandler(
      OpenTelemetrySdk openTelemetrySdk, Duration flushTimeout, AwsLambdaTracer tracer) {
    this.openTelemetrySdk = openTelemetrySdk;
    this.flushTimeoutNanos = flushTimeout.toNanos();
    this.tracer = tracer;
  }

  private Map<String, String> getHeaders(I input) {
    if (input instanceof APIGatewayProxyRequestEvent) {
      APIGatewayProxyRequestEvent event = (APIGatewayProxyRequestEvent) input;
      return event.getHeaders();
    }
    return Collections.emptyMap();
  }

  @Override
  public final O handleRequest(I input, Context context) {
    io.opentelemetry.context.Context otelContext =
        tracer.startSpan(context, SpanKind.SERVER, input, getHeaders(input));
    Throwable error = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      O output = doHandleRequest(input, context);
      tracer.onOutput(otelContext, output);
      return output;
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(otelContext, error);
      } else {
        tracer.end(otelContext);
      }
      openTelemetrySdk
          .getSdkTracerProvider()
          .forceFlush()
          .join(flushTimeoutNanos, TimeUnit.NANOSECONDS);
    }
  }

  protected abstract O doHandleRequest(I input, Context context);
}
