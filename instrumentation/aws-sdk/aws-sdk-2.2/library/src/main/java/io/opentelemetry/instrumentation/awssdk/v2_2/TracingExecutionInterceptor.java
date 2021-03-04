/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DynamoDB;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
  static final ExecutionAttribute<AwsSdkRequest> AWS_SDK_REQUEST_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".AwsSdkRequest");

  static final String COMPONENT_NAME = "java-aws-sdk";

  private final AwsSdkHttpClientTracer tracer;
  private final boolean captureExperimentalSpanAttributes;
  private final FieldMapper fieldMapper;

  TracingExecutionInterceptor(
      AwsSdkHttpClientTracer tracer, boolean captureExperimentalSpanAttributes) {
    this.tracer = tracer;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    fieldMapper = new FieldMapper();
  }

  @Override
  public void beforeExecution(
      Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context parentOtelContext = io.opentelemetry.context.Context.current();
    if (!tracer.shouldStartSpan(parentOtelContext)) {
      return;
    }
    io.opentelemetry.context.Context otelContext =
        tracer.startSpan(parentOtelContext, executionAttributes);
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, otelContext);
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
    tracer.inject(otelContext, builder);
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
    tracer.onRequest(span, context.httpRequest());

    AwsSdkRequest awsSdkRequest = AwsSdkRequest.ofSdkRequest(context.request());
    if (awsSdkRequest != null) {
      executionAttributes.putAttribute(AWS_SDK_REQUEST_ATTRIBUTE, awsSdkRequest);
      populateRequestAttributes(span, awsSdkRequest, context.request(), executionAttributes);
    }
    populateGenericAttributes(span, executionAttributes);
  }

  private void populateRequestAttributes(
      Span span,
      AwsSdkRequest awsSdkRequest,
      SdkRequest sdkRequest,
      ExecutionAttributes attributes) {

    fieldMapper.mapToAttributes(sdkRequest, awsSdkRequest, span);

    if (awsSdkRequest.type() == DynamoDB) {
      span.setAttribute(SemanticAttributes.DB_SYSTEM, "dynamodb");
      String operation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);
      if (operation != null) {
        span.setAttribute(SemanticAttributes.DB_OPERATION, operation);
      }
    }
  }

  private void populateGenericAttributes(Span span, ExecutionAttributes attributes) {
    if (captureExperimentalSpanAttributes) {
      String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
      String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

      span.setAttribute("aws.agent", COMPONENT_NAME);
      span.setAttribute("aws.service", awsServiceName);
      span.setAttribute("aws.operation", awsOperation);
    }
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
    onSdkResponse(span, context.response(), executionAttributes);
    tracer.end(otelContext, context.httpResponse());
  }

  // Certain headers in the request like User-Agent are only available after execution.
  private void onUserAgentHeaderAvailable(Span span, SdkHttpRequest request) {
    span.setAttribute(
        SemanticAttributes.HTTP_USER_AGENT, tracer.requestHeader(request, "User-Agent"));
  }

  private void onSdkResponse(
      Span span, SdkResponse response, ExecutionAttributes executionAttributes) {
    if (captureExperimentalSpanAttributes) {
      if (response instanceof AwsResponse) {
        span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
      }
      AwsSdkRequest sdkRequest = executionAttributes.getAttribute(AWS_SDK_REQUEST_ATTRIBUTE);
      if (sdkRequest != null) {
        fieldMapper.mapToAttributes(response, sdkRequest, span);
      }
    }
  }

  @Override
  public void onExecutionFailure(
      Context.FailedExecution context, ExecutionAttributes executionAttributes) {
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    clearAttributes(executionAttributes);
    tracer.endExceptionally(otelContext, context.exception());
  }

  private void clearAttributes(ExecutionAttributes executionAttributes) {
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, null);
    executionAttributes.putAttribute(AWS_SDK_REQUEST_ATTRIBUTE, null);
  }

  /**
   * Returns the {@link Context} stored in the {@link ExecutionAttributes}, or {@code null} if there
   * is no operation set.
   */
  private static io.opentelemetry.context.Context getContext(ExecutionAttributes attributes) {
    return attributes.getAttribute(CONTEXT_ATTRIBUTE);
  }
}
