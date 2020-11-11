/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsLambdaTracer extends BaseTracer {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";

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

  private io.opentelemetry.context.Context parent(@Nullable Map<String, String> headers) {

    io.opentelemetry.context.Context parentContext = null;
    String parentTraceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    if (parentTraceHeader != null) {
      parentContext = ParentContextExtractor.fromXRayHeader(parentTraceHeader);
    }
    if (!isValid(parentContext) && (headers != null)) {
      // try http
      parentContext = ParentContextExtractor.fromHttpHeaders(headers);
    }

    return parentContext;
  }

  SpanBuilder createSpan(Context context, @Nullable Map<String, String> headers) {
    SpanBuilder span = tracer.spanBuilder(context.getFunctionName());
    span.setAttribute(SemanticAttributes.FAAS_EXECUTION, context.getAwsRequestId());
    io.opentelemetry.context.Context parent = parent(headers);
    if (parent != null) {
      span.setParent(parent);
    }
    return span;
  }

  public Span startSpan(Context context, Kind kind, Map<String, String> headers) {
    return createSpan(context, headers).setSpanKind(kind).startSpan();
  }

  public Span startSpan(Context context, Kind kind) {
    return createSpan(context, null).setSpanKind(kind).startSpan();
  }

  /** Creates new scoped context with the given span. */
  @Override
  public Scope startScope(Span span) {
    // TODO we could do this in one go, but TracingContextUtils.CONTEXT_SPAN_KEY is private
    io.opentelemetry.context.Context newContext =
        io.opentelemetry.context.Context.current().with(CONTEXT_SERVER_SPAN_KEY, span).with(span);
    return newContext.makeCurrent();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.aws-lambda";
  }
}
