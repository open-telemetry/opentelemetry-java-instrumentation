/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_EXECUTION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_TRIGGER;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FaasTriggerValues;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsLambdaTracer extends BaseTracer {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";
  private static final MethodHandle GET_FUNCTION_ARN;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    MethodHandle getFunctionArn;
    try {
      getFunctionArn =
          lookup.findVirtual(
              Context.class, "getInvokedFunctionArn", MethodType.methodType(String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      getFunctionArn = null;
    }
    GET_FUNCTION_ARN = getFunctionArn;
  }

  private final HttpSpanAttributes httpSpanAttributes = new HttpSpanAttributes();
  // cached accountId value
  private volatile String accountId;

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
    setCommonAttributes(span, context);
    if (input instanceof APIGatewayProxyRequestEvent) {
      span.setAttribute(FAAS_TRIGGER, FaasTriggerValues.HTTP.getValue());
      httpSpanAttributes.onRequest(span, (APIGatewayProxyRequestEvent) input);
    }
  }

  private void setCommonAttributes(SpanBuilder span, Context context) {
    span.setAttribute(FAAS_EXECUTION, context.getAwsRequestId());
    String arn = getFunctionArn(context);
    if (arn != null) {
      span.setAttribute(FAAS_ID, arn);
    }
    String accountId = getAccountId(arn);
    if (accountId != null) {
      span.setAttribute(CLOUD_ACCOUNT_ID, accountId);
    }
  }

  @Nullable
  private String getFunctionArn(Context context) {
    if (GET_FUNCTION_ARN == null) {
      return null;
    }
    try {
      return (String) GET_FUNCTION_ARN.invoke(context);
    } catch (Throwable throwable) {
      return null;
    }
  }

  @Nullable
  private String getAccountId(@Nullable String arn) {
    if (arn == null) {
      return null;
    }
    if (accountId == null) {
      synchronized (this) {
        if (accountId == null) {
          String[] arnParts = arn.split(":");
          if (arnParts.length >= 5) {
            accountId = arnParts[4];
          }
        }
      }
    }
    return accountId;
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
    io.opentelemetry.context.Context newContext =
        withServerSpan(io.opentelemetry.context.Context.current(), span);
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
