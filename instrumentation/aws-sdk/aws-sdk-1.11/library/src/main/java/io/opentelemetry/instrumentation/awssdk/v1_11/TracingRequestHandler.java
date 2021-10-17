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
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.List;
import javax.annotation.Nullable;

/** Tracing Request Handler. */
final class TracingRequestHandler extends RequestHandler2 {

  static final HandlerContextKey<Context> CONTEXT =
      new HandlerContextKey<>(Context.class.getName());

  private final Instrumenter<Request<?>, Response<?>> requestInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> consumerInstrumenter;

  TracingRequestHandler(
      Instrumenter<Request<?>, Response<?>> requestInstrumenter,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter) {
    this.requestInstrumenter = requestInstrumenter;
    this.consumerInstrumenter = consumerInstrumenter;
  }

  @Override
  public void beforeRequest(Request<?> request) {
    Context parentContext = Context.current();
    if (!requestInstrumenter.shouldStart(parentContext, request)) {
      return;
    }
    Context context = requestInstrumenter.start(parentContext, request);

    AwsXrayPropagator.getInstance().inject(context, request, AwsSdkInjectAdapter.INSTANCE);

    request.addHandlerContext(CONTEXT, context);
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
    Context context = consumerInstrumenter.start(parentContext, request);
    consumerInstrumenter.end(context, request, response, null);
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
    requestInstrumenter.end(context, request, response, error);
  }
}
