/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.api.trace.attributes.SemanticAttributes.FAAS_EXECUTION;
import static io.opentelemetry.api.trace.attributes.SemanticAttributes.FAAS_TRIGGER;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes.FaasTriggerValues;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;
import java.util.Collections;
import java.util.Map;

public class AwsLambdaTracer extends BaseInstrumenter {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";

  private final HttpSpanAttributes httpSpanAttributes = new HttpSpanAttributes();

  public AwsLambdaTracer() {}

  public AwsLambdaTracer(Tracer tracer) {
    super(tracer);
  }

  private boolean isValid(io.opentelemetry.context.Context context) {
    if (context == null) {
      return false;
    }
    Span parentSpan = Span.fromContext(context);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    return parentSpanContext.isValid();
  }

  private io.opentelemetry.context.Context parent(Map<String, String> headers) {

    io.opentelemetry.context.Context parentContext = null;
    String parentTraceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    if (parentTraceHeader != null) {
      parentContext = ParentContextExtractor.fromXRayHeader(parentTraceHeader);
    }
    if (!isValid(parentContext)) {
      // try http
      parentContext = ParentContextExtractor.fromHttpHeaders(headers);
    }

    return parentContext;
  }

  private SpanBuilder createSpan(Context context, Object input, Map<String, String> headers) {
    SpanBuilder span = tracer.spanBuilder(spanName(context, input));
    setAttributes(span, context, input);
    io.opentelemetry.context.Context parent = parent(headers);
    if (parent != null) {
      span.setParent(parent);
    }
    return span;
  }

  private void setAttributes(SpanBuilder span, Context context, Object input) {
    span.setAttribute(FAAS_EXECUTION, context.getAwsRequestId());
    if (input instanceof APIGatewayProxyRequestEvent) {
      span.setAttribute(FAAS_TRIGGER, FaasTriggerValues.HTTP.getValue());
      httpSpanAttributes.onRequest(span, (APIGatewayProxyRequestEvent) input);
    }
  }

  private String spanName(Context context, Object input) {
    String name = null;
    if (input instanceof APIGatewayProxyRequestEvent) {
      name = ((APIGatewayProxyRequestEvent) input).getResource();
    }
    return name == null ? context.getFunctionName() : name;
  }

  public Span startSpan(Context context, Kind kind, Object input, Map<String, String> headers) {
    return createSpan(context, input, headers).setSpanKind(kind).startSpan();
  }

  public Span startSpan(Context context, Object input, Kind kind) {
    return createSpan(context, input, Collections.emptyMap()).setSpanKind(kind).startSpan();
  }

  /** Creates new scoped context with the given span. */
  @Override
  public Scope startScope(Span span) {
    // TODO we could do this in one go, but TracingContextUtils.CONTEXT_SPAN_KEY is private
    io.opentelemetry.context.Context newContext =
        io.opentelemetry.context.Context.current()
            .with(io.opentelemetry.instrumentation.api.tracer.Tracer.CONTEXT_SERVER_SPAN_KEY, span)
            .with(span);
    return newContext.makeCurrent();
  }

  public void onOutput(Span span, Object output) {
    if (output instanceof APIGatewayProxyResponseEvent) {
      httpSpanAttributes.onResponse(span, (APIGatewayProxyResponseEvent) output);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.aws-lambda";
  }
}
