/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.CLOUD_ACCOUNT_ID;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.FAAS_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_EXECUTION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_TRIGGER;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FaasTriggerValues;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsLambdaTracer extends BaseTracer {

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

  public AwsLambdaTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
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

  public io.opentelemetry.context.Context startSpan(
      Context awsContext, SpanKind kind, Object input) {
    return startSpan(awsContext, kind, input, Collections.emptyMap());
  }

  public io.opentelemetry.context.Context startSpan(
      Context awsContext, SpanKind kind, Object input, Map<String, String> headers) {
    io.opentelemetry.context.Context parentContext = ParentContextExtractor.extract(headers, this);

    SpanBuilder spanBuilder = tracer.spanBuilder(spanName(awsContext, input));
    setAttributes(spanBuilder, awsContext, input);
    Span span = spanBuilder.setParent(parentContext).setSpanKind(kind).startSpan();

    return withServerSpan(parentContext, span);
  }

  public void onOutput(io.opentelemetry.context.Context context, Object output) {
    if (output instanceof APIGatewayProxyResponseEvent) {
      httpSpanAttributes.onResponse(
          Span.fromContext(context), (APIGatewayProxyResponseEvent) output);
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.aws-lambda-1.0";
  }
}
