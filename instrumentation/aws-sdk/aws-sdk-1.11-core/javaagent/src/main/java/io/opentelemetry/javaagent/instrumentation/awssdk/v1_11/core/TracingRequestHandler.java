/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.core;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.core.AwsSdkClientTracer.tracer;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.ContextScopePair;
import io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta;

/** Tracing Request Handler. */
public class TracingRequestHandler extends RequestHandler2 {

  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  public static final HandlerContextKey<ContextScopePair> CONTEXT_SCOPE_PAIR_CONTEXT_KEY =
      new HandlerContextKey<>(RequestMeta.class.getName() + ".ContextScopePair");

  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public TracingRequestHandler(ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void beforeRequest(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    RequestMeta requestMeta = contextStore.get(originalRequest);
    Context parentContext = Context.current();
    if (!tracer().shouldStartSpan(parentContext)) {
      return;
    }
    Context context = tracer().startSpan(parentContext, request, requestMeta);
    Scope scope = context.makeCurrent();
    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, new ContextScopePair(context, scope));
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {

    ContextScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY);
    if (scope == null) {
      return;
    }

    request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, null);
    scope.closeScope();
    tracer().end(scope.getContext(), response);
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
