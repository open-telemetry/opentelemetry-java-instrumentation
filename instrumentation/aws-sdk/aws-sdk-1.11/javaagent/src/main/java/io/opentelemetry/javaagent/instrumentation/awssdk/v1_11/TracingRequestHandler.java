/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkClientTracer.CONTEXT_SCOPE_PAIR_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkClientTracer.tracer;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;

/** Tracing Request Handler. */
public class TracingRequestHandler extends RequestHandler2 {

  @Override
  public void beforeRequest(Request<?> request) {

    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    SpanKind kind = (isSqsProducer(originalRequest) ? SpanKind.PRODUCER : SpanKind.CLIENT);

    Context parentContext = Context.current();
    if (!tracer().shouldStartSpan(parentContext)) {
      return;
    }
    Context context = tracer().startSpan(kind, parentContext, request);
    Scope scope = context.makeCurrent();
    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, new ContextScopePair(context, scope));
  }

  private boolean isSqsProducer(AmazonWebServiceRequest request) {
    return request
        .getClass()
        .getName()
        .equals("com.amazonaws.services.sqs.model.SendMessageRequest");
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    if (SqsReceiveMessageRequestAccess.isInstance(request)) {
      SqsReceiveMessageRequestAccess.withAttributeNames(
          request, SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
    }
    return request;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    if (SqsReceiveMessageRequestAccess.isInstance(request.getOriginalRequest())) {
      afterConsumerResponse(request, response);
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

  /** Create and close CONSUMER span for each message consumed. */
  private void afterConsumerResponse(Request<?> request, Response<?> response) {
    Object receiveMessageResult = response.getAwsResponse();
    List<?> messages = SqsReceiveMessageResultAccess.getMessages(receiveMessageResult);
    for (Object message : messages) {
      createConsumerSpan(message, request, response);
    }
  }

  private void createConsumerSpan(Object message, Request<?> request, Response<?> response) {
    Context parentContext =
        SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));
    Context context = tracer().startSpan(SpanKind.CONSUMER, parentContext, request);
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
