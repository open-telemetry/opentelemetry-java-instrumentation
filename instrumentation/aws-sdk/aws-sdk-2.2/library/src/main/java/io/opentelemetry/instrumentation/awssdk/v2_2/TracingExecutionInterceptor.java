/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DYNAMODB;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
  static final ExecutionAttribute<SdkRequest> SDK_REQUEST_ATTRIBUTE =
      new ExecutionAttribute<>(TracingExecutionInterceptor.class.getName() + ".SdkRequest");

  private final Instrumenter<ExecutionAttributes, SdkHttpResponse> requestInstrumenter;
  private final Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter;
  private final boolean captureExperimentalSpanAttributes;
  private final FieldMapper fieldMapper;

  TracingExecutionInterceptor(
      Instrumenter<ExecutionAttributes, SdkHttpResponse> requestInstrumenter,
      Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter,
      boolean captureExperimentalSpanAttributes) {
    this.requestInstrumenter = requestInstrumenter;
    this.consumerInstrumenter = consumerInstrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.fieldMapper = new FieldMapper();
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {

    io.opentelemetry.context.Context parentOtelContext = io.opentelemetry.context.Context.current();
    executionAttributes.putAttribute(SDK_REQUEST_ATTRIBUTE, context.request());

    if (!requestInstrumenter.shouldStart(parentOtelContext, executionAttributes)) {
      return;
    }

    SdkHttpRequest httpRequest = context.httpRequest();
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, httpRequest);

    io.opentelemetry.context.Context otelContext =
        requestInstrumenter.start(parentOtelContext, executionAttributes);
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
      requestInstrumenter.end(otelContext, executionAttributes, null, throwable);
      clearAttributes(executionAttributes);
      throw throwable;
    }
  }

  @Override
  public SdkRequest modifyRequest(
      Context.ModifyRequest context, ExecutionAttributes executionAttributes) {
    SdkRequest request = context.request();
    if (SqsReceiveMessageRequestAccess.isInstance(request)) {
      List<String> existingAttributeNames = getAttributeNames(request);
      if (!existingAttributeNames.contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE)) {
        List<String> attributeNames = new ArrayList<>();
        attributeNames.addAll(existingAttributeNames);
        attributeNames.add(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
        SdkRequest.Builder builder = request.toBuilder();
        SqsReceiveMessageRequestAccess.attributeNamesWithStrings(builder, attributeNames);
        return builder.build();
      }
    }
    return request;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<String> getAttributeNames(SdkRequest request) {
    Optional<List> optional = request.getValueForField("AttributeNames", List.class);
    return optional.isPresent() ? (List<String>) optional.get() : Collections.emptyList();
  }

  @Override
  @SuppressWarnings("deprecation") // deprecated class to be updated once published in new location
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {
    SdkHttpRequest httpRequest = context.httpRequest();

    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext == null) {
      return httpRequest;
    }

    SdkHttpRequest.Builder builder = httpRequest.toBuilder();
    AwsXrayPropagator.getInstance().inject(otelContext, builder, RequestHeaderSetter.INSTANCE);
    return builder.build();
  }

  private void populateRequestAttributes(
      Span span,
      AwsSdkRequest awsSdkRequest,
      SdkRequest sdkRequest,
      ExecutionAttributes attributes) {

    fieldMapper.mapToAttributes(sdkRequest, awsSdkRequest, span);

    if (awsSdkRequest.type() == DYNAMODB) {
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
    if (SqsReceiveMessageRequestAccess.isInstance(context.request())) {
      afterConsumerResponse(executionAttributes, context.response(), context.httpResponse());
    }

    // http request has been changed
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, context.httpRequest());
    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    Span span = Span.fromContext(otelContext);
    onUserAgentHeaderAvailable(span, executionAttributes);
    onSdkResponse(span, context.response(), executionAttributes);
    requestInstrumenter.end(otelContext, executionAttributes, context.httpResponse(), null);
    clearAttributes(executionAttributes);
  }

  /** Create and close CONSUMER span for each message consumed. */
  private void afterConsumerResponse(
      ExecutionAttributes executionAttributes, SdkResponse response, SdkHttpResponse httpResponse) {
    List<Object> messages = getMessages(response);
    for (Object message : messages) {
      createConsumerSpan(message, executionAttributes, httpResponse);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<Object> getMessages(SdkResponse response) {
    Optional<List> optional = response.getValueForField("Messages", List.class);
    return optional.isPresent() ? optional.get() : Collections.emptyList();
  }

  private void createConsumerSpan(
      Object message, ExecutionAttributes executionAttributes, SdkHttpResponse httpResponse) {
    io.opentelemetry.context.Context parentContext =
        SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));
    io.opentelemetry.context.Context context =
        consumerInstrumenter.start(parentContext, executionAttributes);
    consumerInstrumenter.end(context, executionAttributes, httpResponse, null);
  }

  // Certain headers in the request like User-Agent are only available after execution.
  private static void onUserAgentHeaderAvailable(Span span, ExecutionAttributes request) {
    List<String> userAgent =
        AwsSdkInstrumenterFactory.httpAttributesGetter.requestHeader(request, "User-Agent");
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
    requestInstrumenter.end(otelContext, executionAttributes, null, context.exception());
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
