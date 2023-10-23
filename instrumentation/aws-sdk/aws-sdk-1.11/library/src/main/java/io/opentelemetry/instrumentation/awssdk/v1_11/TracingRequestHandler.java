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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import java.time.Instant;
import javax.annotation.Nullable;

/** Tracing Request Handler. */
final class TracingRequestHandler extends RequestHandler2 {

  static final HandlerContextKey<Context> CONTEXT =
      new HandlerContextKey<>(Context.class.getName());
  private static final ContextKey<Instant> REQUEST_START_KEY =
      ContextKey.named(TracingRequestHandler.class.getName() + ".RequestStart");
  private static final ContextKey<Context> PARENT_CONTEXT_KEY =
      ContextKey.named(TracingRequestHandler.class.getName() + ".ParentContext");

  private final Instrumenter<Request<?>, Response<?>> requestInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> consumerInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> producerInstrumenter;

  TracingRequestHandler(
      Instrumenter<Request<?>, Response<?>> requestInstrumenter,
      Instrumenter<Request<?>, Response<?>> consumerInstrumenter,
      Instrumenter<Request<?>, Response<?>> producerInstrumenter) {
    this.requestInstrumenter = requestInstrumenter;
    this.consumerInstrumenter = consumerInstrumenter;
    this.producerInstrumenter = producerInstrumenter;
  }

  @Override
  @SuppressWarnings("deprecation") // deprecated class to be updated once published in new location
  public void beforeRequest(Request<?> request) {
    // GeneratePresignedUrlRequest doesn't result in actual request, beforeRequest is the only
    // method called for it. Span created here would never be ended and scope would be leaked when
    // running with java agent.
    if ("com.amazonaws.services.s3.model.GeneratePresignedUrlRequest"
        .equals(request.getOriginalRequest().getClass().getName())) {
      return;
    }

    Instrumenter<Request<?>, Response<?>> instrumenter = getInstrumenter(request);

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return;
    }

    // Skip creating request span for AmazonSQSClient.receiveMessage if there is no parent span and
    // also suppress the span from the underlying http client. Request/http client span appears in a
    // separate trace from message producer/consumer spans if there is no parent span just having
    // a trace with only the request/http client span isn't useful.
    if (Context.root() == parentContext
        && "com.amazonaws.services.sqs.model.ReceiveMessageRequest"
            .equals(request.getOriginalRequest().getClass().getName())) {
      Context context = InstrumenterUtil.suppressSpan(instrumenter, parentContext, request);
      context = context.with(REQUEST_START_KEY, Instant.now());
      context = context.with(PARENT_CONTEXT_KEY, parentContext);
      request.addHandlerContext(CONTEXT, context);
      return;
    }

    Context context = instrumenter.start(parentContext, request);

    AwsXrayPropagator.getInstance().inject(context, request, HeaderSetter.INSTANCE);

    request.addHandlerContext(CONTEXT, context);
  }

  @Override
  @CanIgnoreReturnValue
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    // TODO: We are modifying the request in-place instead of using clone() as recommended
    //  by the Javadoc in the interface.
    SqsAccess.beforeMarshalling(request);
    return request;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    SqsAccess.afterResponse(request, response, consumerInstrumenter);
    finish(request, response, null);
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

    Instrumenter<Request<?>, Response<?>> instrumenter = getInstrumenter(request);

    // see beforeRequest, requestStart is only set when we skip creating request span for sqs
    // AmazonSQSClient.receiveMessage calls
    Instant requestStart = context.get(REQUEST_START_KEY);
    if (requestStart != null) {
      Context parentContext = context.get(PARENT_CONTEXT_KEY);
      // create request span if there was an error
      if (error != null && requestInstrumenter.shouldStart(parentContext, request)) {
        InstrumenterUtil.startAndEnd(
            instrumenter, parentContext, request, response, error, requestStart, Instant.now());
      }
      return;
    }

    instrumenter.end(context, request, response, error);
  }

  private Instrumenter<Request<?>, Response<?>> getInstrumenter(Request<?> request) {
    boolean isSqsProducer =
        "com.amazonaws.services.sqs.model.SendMessageRequest"
            .equals(request.getOriginalRequest().getClass().getName());
    return isSqsProducer ? producerInstrumenter : requestInstrumenter;
  }
}
