/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkClientTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta.CONTEXT_SCOPE_PAIR_CONTEXT_KEY;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.List;

/** Tracing Request Handler. */
public class TracingRequestHandler extends RequestHandler2 {

  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public TracingRequestHandler(ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void beforeRequest(Request<?> request) {

    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    Span.Kind kind = (isSqsProducer(originalRequest) ? Span.Kind.PRODUCER : Span.Kind.CLIENT);

    RequestMeta requestMeta = contextStore.get(originalRequest);
    Context parentContext = Context.current();
    if (!tracer().shouldStartSpan(parentContext)) {
      return;
    }
    Context context = tracer().startSpan(kind, parentContext, request, requestMeta);
    Scope scope = context.makeCurrent();
    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, new ContextScopePair(context, scope));
  }

  private boolean isSqsProducer(AmazonWebServiceRequest request) {
    return (request instanceof SendMessageRequest);
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    if (isSqsConsumer(request)) {
      ReceiveMessageRequest receiveMessageRequest = (ReceiveMessageRequest) request;
      receiveMessageRequest.withAttributeNames(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
    }
    return request;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    if (isSqsConsumer(request.getOriginalRequest())) {
      afterConsumerResponse(
          (Request<ReceiveMessageRequest>) request, (Response<ReceiveMessageResult>) response);
    }
    // close outstanding "client" span
    ContextScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY);
    if (scope == null) {
      return;
    }
    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, null);
    scope.closeScope();
    tracer().end(scope.getContext(), response);
  }

  private boolean isSqsConsumer(AmazonWebServiceRequest request) {
    return (request instanceof ReceiveMessageRequest);
  }

  /** Create and close CONSUMER span for each message consumed. */
  private void afterConsumerResponse(
      Request<ReceiveMessageRequest> request, Response<ReceiveMessageResult> response) {
    ReceiveMessageResult receiveMessageResult = response.getAwsResponse();
    List<Message> messages = receiveMessageResult.getMessages();
    for (Message message : messages) {
      createConsumerSpan(message, request, response);
    }
  }

  private void createConsumerSpan(Message message, Request<?> request, Response<?> response) {
    Context parentContext = SqsParentContext.ofSystemAttributes(message.getAttributes());
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    RequestMeta requestMeta = contextStore.get(originalRequest);
    Context context = tracer().startSpan(Span.Kind.CONSUMER, parentContext, request, requestMeta);
    tracer().end(context, response);
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    ContextScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY);
    if (scope == null) {
      return;
    }
    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, null);
    scope.closeScope();
    tracer().endExceptionally(scope.getContext(), e);
  }
}
