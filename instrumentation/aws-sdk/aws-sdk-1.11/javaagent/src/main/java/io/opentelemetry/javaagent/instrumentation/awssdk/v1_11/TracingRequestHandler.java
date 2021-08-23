/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTracing;

/**
 * A {@link RequestHandler2} for use in the agent. Unlike library instrumentation, the agent will
 * also instrument the underlying HTTP client, and we must set the context as current to be able to
 * suppress it. Also unlike library instrumentation, we are able to instrument the SDK's internal
 * classes to handle buggy behavior related to exceptions that can cause scopes to never be closed
 * otherwise which would be disastrous. We hope there won't be anymore significant changes to this
 * legacy SDK that would cause these workarounds to break in the future.
 */
// NB: If the error-handling workarounds stop working, we should consider introducing the same
// x-amzn-request-id header check in Apache instrumentation for suppressing spans that we have in
// Netty instrumentation.
public class TracingRequestHandler extends RequestHandler2 {

  public static final HandlerContextKey<Scope> SCOPE =
      new HandlerContextKey<>(Scope.class.getName());

  public static final RequestHandler2 tracingHandler =
      AwsSdkTracing.newBuilder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes", false))
          .build()
          .newRequestHandler();

  @Override
  public void beforeRequest(Request<?> request) {
    tracingHandler.beforeRequest(request);
    Context context = AwsSdkTracing.getOpenTelemetryContext(request);
    // it is possible that context is not  set by lib's handler
    if (context != null) {
      Scope scope = context.makeCurrent();
      request.addHandlerContext(SCOPE, scope);
    }
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    return tracingHandler.beforeMarshalling(request);
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    tracingHandler.afterResponse(request, response);
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    tracingHandler.afterError(request, response, e);
    finish(request);
  }

  private static void finish(Request<?> request) {
    Scope scope = request.getHandlerContext(SCOPE);
    if (scope == null) {
      return;
    }
    scope.close();
    request.addHandlerContext(SCOPE, null);
  }
}
