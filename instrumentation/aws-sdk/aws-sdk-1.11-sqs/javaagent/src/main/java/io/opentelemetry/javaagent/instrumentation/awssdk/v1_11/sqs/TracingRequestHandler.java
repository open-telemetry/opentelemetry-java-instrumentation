/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.sqs;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.sqs.AwsSdkQueueTracer.tracer;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.ContextScopePair;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta;
import java.util.Map;

/** Tracing Request Handler for SQS messages. */
public class TracingRequestHandler extends RequestHandler2 {

  /** Separate context key - to avoid mixing core ans sqs instrumentations. */
  private static final HandlerContextKey<ContextScopePair> CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY =
      new HandlerContextKey<>(TracingRequestHandler.class.getName() + ".ContextScopePair");

  private static class SqsSetter implements TextMapPropagator.Setter<Request<SendMessageRequest>> {

    private static final SqsSetter INSTANCE = new SqsSetter();

    public void set(Request<SendMessageRequest> carrier, String key, String value) {

      SendMessageRequest originalRequest = (SendMessageRequest) carrier.getOriginalRequest();
      setMessageAttribute(originalRequest, key, value);
      int messageAttributesListIndex = originalRequest.getMessageAttributes().size();
      setRequestParameter(carrier, messageAttributesListIndex, key, value);
    }

    private void setRequestParameter(
        Request<SendMessageRequest> carrier,
        int messageAttributesListIndex,
        String key,
        String value) {
      carrier.addParameter("MessageAttribute." + messageAttributesListIndex + ".Name", key);
      carrier.addParameter(
          "MessageAttribute." + messageAttributesListIndex + ".Value.StringValue", value);
      carrier.addParameter(
          "MessageAttribute." + messageAttributesListIndex + ".Value.DataType", "String");
    }

    private void setMessageAttribute(SendMessageRequest originalRequest, String key, String value) {
      final MessageAttributeValue sqsValue = new MessageAttributeValue();
      sqsValue.setStringValue(value);
      sqsValue.setDataType("String");
      originalRequest.addMessageAttributesEntry(key, sqsValue);
    }
  }

  private static class SqsGetter
      implements TextMapPropagator.Getter<Map<String, MessageAttributeValue>> {

    private static final SqsGetter INSTANCE = new SqsGetter();

    @Override
    public Iterable<String> keys(Map<String, MessageAttributeValue> attributes) {
      return attributes.keySet();
    }

    @Override
    public String get(Map<String, MessageAttributeValue> attributes, String key) {
      return (attributes.containsKey(key) ? attributes.get(key).getStringValue() : null);
    }
  }

  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public TracingRequestHandler(ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  private boolean isProducerRequest(AmazonWebServiceRequest originalRequest) {
    return (originalRequest instanceof SendMessageRequest);
  }

  private boolean isConsumerRequest(AmazonWebServiceRequest originalRequest) {
    return (originalRequest instanceof ReceiveMessageRequest);
  }

  /** Creates PRODUCER span for a message sent. */
  @Override
  public void beforeRequest(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    if (isProducerRequest(originalRequest)) {
      RequestMeta requestMeta = contextStore.get(originalRequest);
      Context context =
          tracer().startSpan(Span.Kind.PRODUCER, Context.current(), request, requestMeta);
      Scope scope = context.makeCurrent();
      request.addHandlerContext(
          CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY, new ContextScopePair(context, scope));

      // need to inject propagation fields as attributes / parameters
      GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .inject(Context.current(), (Request<SendMessageRequest>) request, SqsSetter.INSTANCE);
    }
  }

  /**
   * Sets all propagation fields as message attributes that should be accepted (if available in the
   * message).
   */
  @Override
  public AmazonWebServiceRequest beforeExecution(AmazonWebServiceRequest request) {
    if (isConsumerRequest(request)) {
      ((ReceiveMessageRequest) request)
          .withMessageAttributeNames(
              GlobalOpenTelemetry.getPropagators().getTextMapPropagator().fields());
    }
    return request;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    if (isConsumerRequest(request.getOriginalRequest())) {
      afterConsumerResponse(request, response);
    } else if (isProducerRequest(request.getOriginalRequest())) {
      afterProducerResponse(request, response);
    }
  }

  /** Create and close CONSUMER span for each message consumed. */
  private void afterConsumerResponse(Request<?> request, Response<?> response) {
    ReceiveMessageResult result = (ReceiveMessageResult) response.getAwsResponse();
    for (Message message : result.getMessages()) {
      Context parentContext = getOrCreateParent(message);
      AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
      RequestMeta requestMeta = contextStore.get(originalRequest);
      Context context = tracer().startSpan(Span.Kind.CONSUMER, parentContext, request, requestMeta);
      tracer().end(context, response);
    }
  }

  /** Parent context can be extracted from SQS message attributes. */
  private Context getOrCreateParent(Message message) {
    Map<String, MessageAttributeValue> attributes = message.getMessageAttributes();
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.current(), attributes, SqsGetter.INSTANCE);
  }

  /** Close already created PRODUCER span. */
  private void afterProducerResponse(Request<?> request, Response<?> response) {
    ContextScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY);
    if (scope == null) {
      return;
    }

    request.addHandlerContext(CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY, null);
    scope.closeScope();
    tracer().end(scope.getContext(), response);
  }

  /** Close already created PRODUCER span. */
  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    if (isProducerRequest(request.getOriginalRequest())) {
      ContextScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY);
      if (scope == null) {
        return;
      }
      request.addHandlerContext(CONTEXT_SCOPE_PAIR_SQS_CONTEXT_KEY, null);
      scope.closeScope();
      tracer().endExceptionally(Span.fromContext(scope.getContext()), e);
    }
  }
}
