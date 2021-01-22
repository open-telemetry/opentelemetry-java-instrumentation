/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdk.getContext;
import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkHttpClientTracer.tracer;
import static io.opentelemetry.instrumentation.awssdk.v2_2.RequestType.ofSdkRequest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ClientType;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;

/** AWS request execution interceptor. */
final class TracingExecutionInterceptor implements ExecutionInterceptor {

  // the class name is part of the attribute name, so that it will be shaded when used in javaagent
  // instrumentation, and won't conflict with usage outside javaagent instrumentation
  static final ExecutionAttribute<io.opentelemetry.context.Context> CONTEXT_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".Context");
  static final ExecutionAttribute<Scope> SCOPE_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".Scope");
  static final ExecutionAttribute<RequestType> REQUEST_TYPE_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".RequestType");

  static final String COMPONENT_NAME = "java-aws-sdk";

  private static final Map<RequestType, SdkRequestDecorator> TYPE_TO_DECORATOR = mapDecorators();
  private static final Map<String, String> FIELD_TO_ATTRIBUTE = mapFieldToAttribute();

  private static Map<RequestType, SdkRequestDecorator> mapDecorators() {
    Map<RequestType, SdkRequestDecorator> result = new EnumMap<>(RequestType.class);
    result.put(RequestType.DynamoDB, new DbRequestDecorator());
    return result;
  }

  private static Map<String, String> mapFieldToAttribute() {
    Map<String, String> result = new HashMap<>();
    result.put("QueueUrl", "aws.queue.url");
    result.put("Bucket", "aws.bucket.name");
    result.put("QueueName", "aws.queue.name");
    result.put("StreamName", "aws.stream.name");
    result.put("TableName", "aws.table.name");
    return result;
  }

  @Nullable
  private SdkRequestDecorator decorator(ExecutionAttributes executionAttributes) {
    RequestType type = getTypeFromAttributes(executionAttributes);
    return TYPE_TO_DECORATOR.get(type);
  }

  private RequestType getTypeFromAttributes(ExecutionAttributes executionAttributes) {
    return executionAttributes.getAttribute(REQUEST_TYPE_ATTRIBUTE);
  }

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context parentOtelContext = io.opentelemetry.context.Context.current();
    if (!tracer().shouldStartSpan(parentOtelContext)) {
      return;
    }
    io.opentelemetry.context.Context otelContext =
        tracer().startSpan(parentOtelContext, executionAttributes);
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, otelContext);
    RequestType type = ofSdkRequest(context.request());
    if (type != null) {
      executionAttributes.putAttribute(REQUEST_TYPE_ATTRIBUTE, type);
    }
    if (executionAttributes
        .getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
        .equals(ClientType.SYNC)) {
      // We can only activate context for synchronous clients, which allows downstream
      // instrumentation like Apache to know about the SDK span.
      executionAttributes.putAttribute(SCOPE_ATTRIBUTE, otelContext.makeCurrent());
    }
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext == null) {
      return context.httpRequest();
    }

    SdkHttpRequest.Builder builder = context.httpRequest().toBuilder();
    tracer().inject(otelContext, builder);
    return builder.build();
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext == null) {
      return;
    }

    Span span = Span.fromContext(otelContext);
    tracer().onRequest(span, context.httpRequest());
    SdkRequestDecorator decorator = decorator(executionAttributes);
    if (decorator != null) {
      decorator.decorate(span, context.request(), executionAttributes);
    }
    decorateWithGenericRequestData(span, context.request());
    decorateWithExAttributesData(span, executionAttributes);
  }

  private void decorateWithGenericRequestData(Span span, SdkRequest request) {

    RequestType type = ofSdkRequest(request);
    if (type != null) {
      for (String field : type.getFields()) {
        request
            .getValueForField(field, String.class)
            .ifPresent(val -> span.setAttribute(FIELD_TO_ATTRIBUTE.get(field), val));
      }
    }
  }

  private void decorateWithExAttributesData(Span span, ExecutionAttributes attributes) {

    String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation);
  }

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    Scope scope = executionAttributes.getAttribute(SCOPE_ATTRIBUTE);
    if (scope != null) {
      scope.close();
    }
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    clearAttributes(executionAttributes);
    Span span = Span.fromContext(otelContext);
    onUserAgentHeaderAvailable(span, context.httpRequest());
    onSdkResponse(span, context.response());
    tracer().end(otelContext, context.httpResponse());
  }

  // Certain headers in the request like User-Agent are only available after execution.
  private void onUserAgentHeaderAvailable(Span span, SdkHttpRequest request) {
    span.setAttribute(
        SemanticAttributes.HTTP_USER_AGENT, tracer().requestHeader(request, "User-Agent"));
  }

  private void onSdkResponse(Span span, SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    clearAttributes(executionAttributes);
    tracer().endExceptionally(otelContext, context.exception());
  }

  private void clearAttributes(ExecutionAttributes executionAttributes) {
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, null);
    executionAttributes.putAttribute(REQUEST_TYPE_ATTRIBUTE, null);
  }
}
