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
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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
  @SuppressWarnings("deprecation") // deprecated class to be updated once published in new location
  public void beforeRequest(Request<?> request) {
    // GeneratePresignedUrlRequest doesn't result in actual request, beforeRequest is the only
    // method called for it. Span created here would never be ended and scope would be leaked when
    // running with java agent.
    if ("com.amazonaws.services.s3.model.GeneratePresignedUrlRequest"
        .equals(request.getOriginalRequest().getClass().getName())) {
      return;
    }

    Context parentContext = Context.current();
    if (!requestInstrumenter.shouldStart(parentContext, request)) {
      return;
    }
    Context context = requestInstrumenter.start(parentContext, request);

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
    requestInstrumenter.end(context, request, response, error);
  }
}
