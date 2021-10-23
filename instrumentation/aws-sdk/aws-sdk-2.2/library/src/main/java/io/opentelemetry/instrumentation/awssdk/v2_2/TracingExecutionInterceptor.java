/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DynamoDB;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
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
import software.amazon.awssdk.http.SdkHttpResponse;

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
  static final ExecutionAttribute<SdkHttpRequest> SDK_HTTP_REQUEST_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".SdkHttpRequest");

  private final Instrumenter<ExecutionAttributes, SdkHttpResponse> instrumenter;
  private final boolean captureExperimentalSpanAttributes;
  private final FieldMapper fieldMapper;

  TracingExecutionInterceptor(
      Instrumenter<ExecutionAttributes, SdkHttpResponse> instrumenter,
      boolean captureExperimentalSpanAttributes) {
    this.instrumenter = instrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.fieldMapper = new FieldMapper();
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {

    io.opentelemetry.context.Context parentOtelContext = io.opentelemetry.context.Context.current();

    if (!instrumenter.shouldStart(parentOtelContext, executionAttributes)) {
      return;
    }

    SdkHttpRequest httpRequest = context.httpRequest();
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, httpRequest);

    io.opentelemetry.context.Context otelContext =
        instrumenter.start(parentOtelContext, executionAttributes);
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, otelContext);
    if (executionAttributes
        .getAttribute(SdkExecutionAttribute.CLIENT_TYPE)
        .equals(ClientType.SYNC)) {
      // We can only activate context for synchronous clients, which allows downstream
      // instrumentation like Apache to know about the SDK span.
      executionAttributes.putAttribute(SCOPE_ATTRIBUTE, otelContext.makeCurrent());
    }

    Span span = Span.fromContext(otelContext);

    try {
      AwsSdkRequest awsSdkRequest = AwsSdkRequest.ofSdkRequest(context.request());
      if (awsSdkRequest != null) {
        executionAttributes.putAttribute(AWS_SDK_REQUEST_ATTRIBUTE, awsSdkRequest);
        populateRequestAttributes(span, awsSdkRequest, context.request(), executionAttributes);
      }
    } catch (Throwable throwable) {
      instrumenter.end(otelContext, executionAttributes, null, throwable);
      clearAttributes(executionAttributes);
      throw throwable;
    }
  }

  @Override
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    SdkHttpRequest httpRequest = context.httpRequest();

    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext == null) {
      return httpRequest;
    }

    SdkHttpRequest.Builder builder = httpRequest.toBuilder();
    AwsXrayPropagator.getInstance().inject(otelContext, builder, AwsSdkInjectAdapter.INSTANCE);
    return builder.build();
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

  @Override
  public void afterExecution(
      Context.AfterExecution context, ExecutionAttributes executionAttributes) {
    // http request has been changed
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, context.httpRequest());
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    Span span = Span.fromContext(otelContext);
    onUserAgentHeaderAvailable(span, executionAttributes);
    onSdkResponse(span, context.response(), executionAttributes);
    instrumenter.end(otelContext, executionAttributes, context.httpResponse(), null);
    clearAttributes(executionAttributes);
  }

  // Certain headers in the request like User-Agent are only available after execution.
  private static void onUserAgentHeaderAvailable(Span span, ExecutionAttributes request) {
    List<String> userAgent =
        AwsSdkInstrumenterFactory.attributesExtractor.requestHeader(request, "User-Agent");
    if (!userAgent.isEmpty()) {
      span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, userAgent.get(0));
    }
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
    instrumenter.end(otelContext, executionAttributes, null, context.exception());
    clearAttributes(executionAttributes);
  }

  private static void clearAttributes(ExecutionAttributes executionAttributes) {
    Scope scope = executionAttributes.getAttribute(SCOPE_ATTRIBUTE);
    if (scope != null) {
      scope.close();
    }
    executionAttributes.putAttribute(CONTEXT_ATTRIBUTE, null);
    executionAttributes.putAttribute(AWS_SDK_REQUEST_ATTRIBUTE, null);
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, null);
  }

  /**
   * Returns the {@link Context} stored in the {@link ExecutionAttributes}, or {@code null} if there
   * is no operation set.
   */
  static io.opentelemetry.context.Context getContext(ExecutionAttributes attributes) {
    return attributes.getAttribute(CONTEXT_ATTRIBUTE);
  }
}
