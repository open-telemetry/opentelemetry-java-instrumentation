/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.Map;

/**
 * A base class similar to {@link RequestHandler} but will automatically trace invocations of {@link
 * #doHandleRequest(Object, Context)}. For API Gateway requests (ie of APIGatewayProxyRequestEvent
 * type parameter) also HTTP propagation can be enabled.
 */
public abstract class TracingRequestHandler<I, O> implements RequestHandler<I, O> {

  private static final long DEFAULT_FLUSH_TIMEOUT_SECONDS = 1;

  private final AwsLambdaTracer tracer;
  private final long flushTimeout;

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingRequestHandler(long flushTimeout) {
    this.tracer = new AwsLambdaTracer();
    this.flushTimeout = flushTimeout;
  }

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingRequestHandler() {
    this.tracer = new AwsLambdaTracer();
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingRequestHandler(Tracer tracer) {
    this.tracer = new AwsLambdaTracer(tracer);
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaTracer}.
   */
  protected TracingRequestHandler(AwsLambdaTracer tracer) {
    this.tracer = tracer;
    this.flushTimeout = DEFAULT_FLUSH_TIMEOUT_SECONDS;
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
      LambdaUtils.forceFlush();
    }
  }

  protected abstract O doHandleRequest(I input, Context context);
}
