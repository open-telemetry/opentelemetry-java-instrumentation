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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import javax.annotation.Nullable;

/** Tracing Request Handler. */
final class TracingRequestHandler extends RequestHandler2 {

  static final HandlerContextKey<Context> CONTEXT =
      new HandlerContextKey<>(Context.class.getName());
  private static final ContextKey<Context> PARENT_CONTEXT_KEY =
      ContextKey.named(TracingRequestHandler.class.getName() + ".ParentContext");
  private static final ContextKey<Timer> REQUEST_TIMER_KEY =
      ContextKey.named(TracingRequestHandler.class.getName() + ".Timer");
  private static final ContextKey<Boolean> REQUEST_SPAN_SUPPRESSED_KEY =
      ContextKey.named(TracingRequestHandler.class.getName() + ".RequestSpanSuppressed");
  private static final String SEND_MESSAGE_REQUEST_CLASS =
      "com.amazonaws.services.sqs.model.SendMessageRequest";
  private static final String DYNAMODBV2_CLASS_PREFIX = "com.amazonaws.services.dynamodbv2.model.";

  private final Instrumenter<Request<?>, Response<?>> requestInstrumenter;
  private final Instrumenter<SqsReceiveRequest, Response<?>> consumerReceiveInstrumenter;
  private final Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> producerInstrumenter;
  private final Instrumenter<Request<?>, Response<?>> dynamoDbInstrumenter;

  TracingRequestHandler(
      Instrumenter<Request<?>, Response<?>> requestInstrumenter,
      Instrumenter<SqsReceiveRequest, Response<?>> consumerReceiveInstrumenter,
      Instrumenter<SqsProcessRequest, Response<?>> consumerProcessInstrumenter,
      Instrumenter<Request<?>, Response<?>> producerInstrumenter,
      Instrumenter<Request<?>, Response<?>> dynamoDbInstrumenter) {
    this.requestInstrumenter = requestInstrumenter;
    this.consumerReceiveInstrumenter = consumerReceiveInstrumenter;
    this.consumerProcessInstrumenter = consumerProcessInstrumenter;
    this.producerInstrumenter = producerInstrumenter;
    this.dynamoDbInstrumenter = dynamoDbInstrumenter;
  }

  @Override
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
    if (Span.fromContextOrNull(parentContext) == null
        && "com.amazonaws.services.sqs.model.ReceiveMessageRequest"
            .equals(request.getOriginalRequest().getClass().getName())) {
      Context context = InstrumenterUtil.suppressSpan(instrumenter, parentContext, request);
      context = context.with(REQUEST_TIMER_KEY, Timer.start());
      context = context.with(PARENT_CONTEXT_KEY, parentContext);
      context = context.with(REQUEST_SPAN_SUPPRESSED_KEY, true);
      request.addHandlerContext(CONTEXT, context);
      return;
    }

    Context context = instrumenter.start(parentContext, request);
    context = context.with(REQUEST_TIMER_KEY, Timer.start());
    context = context.with(PARENT_CONTEXT_KEY, parentContext);

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

  Instrumenter<SqsReceiveRequest, Response<?>> getConsumerReceiveInstrumenter() {
    return consumerReceiveInstrumenter;
  }

  Instrumenter<SqsProcessRequest, Response<?>> getConsumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    Context context = request.getHandlerContext(CONTEXT);
    if (context == null) {
      return;
    }
    Timer timer = context.get(REQUEST_TIMER_KEY);
    // javaagent instrumentation activates scope for the request span, we need to use the context
    // we stored before creating the request span to avoid making request span the parent of the
    // sqs receive span
    Context parentContext = context.get(PARENT_CONTEXT_KEY);
    SqsAccess.afterResponse(request, response, timer, parentContext, this);
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

    // see beforeRequest, request suppressed is only set when we skip creating request span for sqs
    // AmazonSQSClient.receiveMessage calls
    if (Boolean.TRUE.equals(context.get(REQUEST_SPAN_SUPPRESSED_KEY))) {
      Context parentContext = context.get(PARENT_CONTEXT_KEY);
      Timer timer = context.get(REQUEST_TIMER_KEY);
      // create request span if there was an error
      if (error != null
          && parentContext != null
          && timer != null
          && requestInstrumenter.shouldStart(parentContext, request)) {
        InstrumenterUtil.startAndEnd(
            instrumenter, parentContext, request, response, error, timer.startTime(), timer.now());
      }
      return;
    }
    instrumenter.end(context, request, response, error);
  }

  private Instrumenter<Request<?>, Response<?>> getInstrumenter(Request<?> request) {
    String className = request.getOriginalRequest().getClass().getName();
    if (className.startsWith(DYNAMODBV2_CLASS_PREFIX)) {
      return dynamoDbInstrumenter;
    }
    if (className.equals(SEND_MESSAGE_REQUEST_CLASS)) {
      return producerInstrumenter;
    }
    return requestInstrumenter;
  }
}
