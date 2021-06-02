/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Tracing Request Handler. */
final class TracingRequestHandler extends RequestHandler2 {

  static final HandlerContextKey<Context> CONTEXT =
      new HandlerContextKey<>(Context.class.getName());

  private final AwsSdkClientTracer tracer;

  TracingRequestHandler(AwsSdkClientTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public void beforeRequest(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    SpanKind kind = (isSqsProducer(originalRequest) ? SpanKind.PRODUCER : SpanKind.CLIENT);

    Context parentContext = Context.current();
    if (!tracer.shouldStartSpan(parentContext)) {
      return;
    }
    Context context = tracer.startSpan(kind, parentContext, request);
    request.addHandlerContext(CONTEXT, context);
  }

  private static boolean isSqsProducer(AmazonWebServiceRequest request) {
    return request
        .getClass()
        .getName()
        .equals("com.amazonaws.services.sqs.model.SendMessageRequest");
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    if (SqsReceiveMessageRequestAccess.isInstance(request)) {
      if (!SqsReceiveMessageRequestAccess.getAttributeNames(request)
          .contains(SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE)) {
        SqsReceiveMessageRequestAccess.withAttributeNames(
            request, SqsParentContext.AWS_TRACE_SYSTEM_ATTRIBUTE);
      }
    }
    return request;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    if (SqsReceiveMessageRequestAccess.isInstance(request.getOriginalRequest())) {
      afterConsumerResponse(request, response);
    }
    finish(request, response, null);
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
    Context context = tracer.startSpan(SpanKind.CONSUMER, parentContext, request);
    tracer.end(context, response);
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    finish(request, response, e);
  }

  private void finish(Request<?> request, Response<?> response, @Nullable Throwable error) {
    // close outstanding "client" span
    Context context = request.getHandlerContext(CONTEXT);
    if (context == null) {
      return;
    }
    request.addHandlerContext(CONTEXT, null);
    if (error == null) {
      tracer.end(context, response);
    } else {
      tracer.endExceptionally(context, response, error);
    }
  }
}
