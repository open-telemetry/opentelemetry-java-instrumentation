/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkRequestType.DYNAMODB;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ClientType;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.utils.builder.SdkBuilder;

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
  @Nullable private final TextMapPropagator messagingPropagator;
  private final boolean useXrayPropagator;
  private final FieldMapper fieldMapper;

  TracingExecutionInterceptor(
      Instrumenter<ExecutionAttributes, SdkHttpResponse> requestInstrumenter,
      Instrumenter<ExecutionAttributes, SdkHttpResponse> consumerInstrumenter,
      boolean captureExperimentalSpanAttributes,
      TextMapPropagator messagingPropagator,
      boolean useXrayPropagator) {
    this.requestInstrumenter = requestInstrumenter;
    this.consumerInstrumenter = consumerInstrumenter;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.messagingPropagator = messagingPropagator;
    this.useXrayPropagator = useXrayPropagator;
    this.fieldMapper = new FieldMapper();
  }

  @Override
  public SdkRequest modifyRequest(
      Context.ModifyRequest context, ExecutionAttributes executionAttributes) {

    // This is the latest point where we can start the span, since we might need to inject
    // it into the request payload. This means that HTTP attributes need to be captured later.

    io.opentelemetry.context.Context parentOtelContext = io.opentelemetry.context.Context.current();
    SdkRequest request = context.request();
    executionAttributes.putAttribute(SDK_REQUEST_ATTRIBUTE, request);

    if (!requestInstrumenter.shouldStart(parentOtelContext, executionAttributes)) {
      // NB: We also skip injection in case we don't start.
      return request;
    }

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

    if (SqsReceiveMessageRequestAccess.isInstance(request)) {
      return modifySqsReceiveMessageRequest(request);
    } else if (messagingPropagator != null) {
      if (SqsSendMessageRequestAccess.isInstance(request)) {
        return injectIntoSqsSendMessageRequest(request, otelContext);
      }
      // TODO: Support SendMessageBatchRequest (and thus SendMessageBatchRequestEntry)
    }
    return request;
  }

  private SdkRequest injectIntoSqsSendMessageRequest(
      SdkRequest request, io.opentelemetry.context.Context otelContext) {
    Map<String, SdkPojo> messageAttributes =
        new HashMap<>(SqsSendMessageRequestAccess.messageAttributes(request));
    messagingPropagator.inject(
        otelContext,
        messageAttributes,
        (carrier, k, v) -> {
          @SuppressWarnings("rawtypes")
          SdkBuilder builder = SqsMessageAttributeValueAccess.builder();
          if (builder == null) {
            return;
          }
          builder = SqsMessageAttributeValueAccess.stringValue(builder, v);
          if (builder == null) {
            return;
          }
          builder = SqsMessageAttributeValueAccess.dataType(builder, "String");
          if (builder == null) {
            return;
          }
          carrier.put(k, (SdkPojo) builder.build());
        });
    if (messageAttributes.size() > 10) { // Too many attributes, we don't want to break the call.
      return request;
    }
    SdkRequest.Builder builder = request.toBuilder();
    SqsSendMessageRequestAccess.messageAttributes(builder, messageAttributes);
    return builder.build();
  }

  private SdkRequest modifySqsReceiveMessageRequest(SdkRequest request) {
    boolean hasXrayAttribute = true;
    List<String> existingAttributeNames = null;
    if (useXrayPropagator) {
      existingAttributeNames = SqsReceiveMessageRequestAccess.getAttributeNames(request);
      hasXrayAttribute =
          existingAttributeNames.contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
    }

    boolean hasMessageAttribute = true;
    List<String> existingMessageAttributeNames = null;
    if (messagingPropagator != null) {
      existingMessageAttributeNames =
          SqsReceiveMessageRequestAccess.getMessageAttributeNames(request);
      hasMessageAttribute = existingMessageAttributeNames.containsAll(messagingPropagator.fields());
    }

    if (hasMessageAttribute && hasXrayAttribute) {
      return request;
    }

    SdkRequest.Builder builder = request.toBuilder();
    if (!hasXrayAttribute) {
      List<String> attributeNames = new ArrayList<>(existingAttributeNames);
      attributeNames.add(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
      SqsReceiveMessageRequestAccess.attributeNamesWithStrings(builder, attributeNames);
    }
    if (messagingPropagator != null) {
      List<String> messageAttributeNames = new ArrayList<>(existingMessageAttributeNames);
      for (String field : messagingPropagator.fields()) {
        if (!existingMessageAttributeNames.contains(field)) {
          messageAttributeNames.add(field);
        }
      }
      SqsReceiveMessageRequestAccess.messageAttributeNames(builder, messageAttributeNames);
    }
    return builder.build();
  }

  @Override
  public void afterMarshalling(
      Context.AfterMarshalling context, ExecutionAttributes executionAttributes) {

    // Since we merge the HTTP attributes into an already started span instead of creating a
    // full child span, we have to do some dirty work here.
    //
    // As per HTTP conventions, we should actually only create spans for the "physical" requests but
    // not for the encompassing logical request, see
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/trace/semantic_conventions/http.md#http-request-retries-and-redirects
    // Specific AWS SDK conventions also don't mention this peculiar hybrid span convention, see
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/trace/semantic_conventions/instrumentation/aws-sdk.md
    //
    // TODO: Consider removing net+http conventions & relying on lower-level client instrumentation

    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext == null) {
      // No context, no sense in doing anything else (but this is not expected)
      return;
    }

    SdkHttpRequest httpRequest = context.httpRequest();
    executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, httpRequest);

    // We ought to pass the parent of otelContext here, but we didn't store it, and it shouldn't
    // make a difference (unless we start supporting the http.resend_count attribute in this
    // instrumentation, which, logically, we can't on this level of abstraction)
    onHttpRequestAvailable(executionAttributes, otelContext, Span.fromContext(otelContext));
  }

  private static void onHttpResponseAvailable(
      ExecutionAttributes executionAttributes,
      io.opentelemetry.context.Context otelContext,
      Span span,
      SdkHttpResponse httpResponse) {
    // For the httpAttributesExtractor dance, see afterMarshalling
    AttributesBuilder builder = Attributes.builder(); // NB: UnsafeAttributes are package-private
    AwsSdkInstrumenterFactory.httpAttributesExtractor.onEnd(
        builder, otelContext, executionAttributes, httpResponse, null);
    span.setAllAttributes(builder.build());
  }

  private static void onHttpRequestAvailable(
      ExecutionAttributes executionAttributes,
      io.opentelemetry.context.Context parentContext,
      Span span) {
    AttributesBuilder builder = Attributes.builder(); // NB: UnsafeAttributes are package-private
    AwsSdkInstrumenterFactory.httpAttributesExtractor.onStart(
        builder, parentContext, executionAttributes);
    span.setAllAttributes(builder.build());
  }

  @Override
  @SuppressWarnings("deprecation") // deprecated class to be updated once published in new location
  public SdkHttpRequest modifyHttpRequest(
      Context.ModifyHttpRequest context, ExecutionAttributes executionAttributes) {

    SdkHttpRequest httpRequest = context.httpRequest();

    if (!useXrayPropagator) {
      return httpRequest;
    }

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

    io.opentelemetry.context.Context otelContext = getContext(executionAttributes);
    if (otelContext != null) {
      // http request has been changed
      executionAttributes.putAttribute(SDK_HTTP_REQUEST_ATTRIBUTE, context.httpRequest());

      Span span = Span.fromContext(otelContext);
      onUserAgentHeaderAvailable(span, executionAttributes);
      onSdkResponse(span, context.response(), executionAttributes);

      SdkHttpResponse httpResponse = context.httpResponse();

      onHttpResponseAvailable(
          executionAttributes, otelContext, Span.fromContext(otelContext), httpResponse);
      requestInstrumenter.end(otelContext, executionAttributes, httpResponse, null);
    }
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

  private void createConsumerSpan(
      Object message, ExecutionAttributes executionAttributes, SdkHttpResponse httpResponse) {

    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.root();

    if (messagingPropagator != null) {
      parentContext =
          SqsParentContext.ofMessageAttributes(
              SqsMessageAccess.getMessageAttributes(message), messagingPropagator);
    }

    if (useXrayPropagator && parentContext == io.opentelemetry.context.Context.root()) {
      parentContext = SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));
    }
    if (consumerInstrumenter.shouldStart(parentContext, executionAttributes)) {
      io.opentelemetry.context.Context context =
          consumerInstrumenter.start(parentContext, executionAttributes);

      // TODO: Even if we keep HTTP attributes (see afterMarshalling), does it make sense here
      //  per-message?
      // TODO: Should we really create root spans if we can't extract anything, or should we attach
      //  to the current context?
      consumerInstrumenter.end(context, executionAttributes, httpResponse, null);
    }
  }

  // Certain headers in the request like User-Agent are only available after execution.
  private static void onUserAgentHeaderAvailable(Span span, ExecutionAttributes request) {
    List<String> userAgent =
        AwsSdkInstrumenterFactory.httpAttributesGetter.getHttpRequestHeader(request, "User-Agent");
    if (!userAgent.isEmpty()) {
      span.setAttribute(SemanticAttributes.USER_AGENT_ORIGINAL, userAgent.get(0));
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
    if (otelContext != null) {
      requestInstrumenter.end(otelContext, executionAttributes, null, context.exception());
    }
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  static List<Object> getMessages(SdkResponse response) {
    Optional<List> optional = response.getValueForField("Messages", List.class);
    return optional.isPresent() ? optional.get() : Collections.emptyList();
  }
}
