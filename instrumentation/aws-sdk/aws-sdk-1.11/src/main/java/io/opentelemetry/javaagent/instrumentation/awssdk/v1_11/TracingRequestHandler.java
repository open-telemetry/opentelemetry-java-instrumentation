/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.AwsSdkClientTracer.TRACER;
import static io.opentelemetry.javaagent.instrumentation.awssdk.v1_11.RequestMeta.SPAN_SCOPE_PAIR_CONTEXT_KEY;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {

  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public TracingRequestHandler(ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(AmazonWebServiceRequest request) {
    return request;
  }

  @Override
  public void beforeRequest(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    RequestMeta requestMeta = contextStore.get(originalRequest);
    Span span = TRACER.startSpan(request, requestMeta);
    Scope scope = TRACER.startScope(span, request);
    request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, new SpanWithScope(span, scope));
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    SpanWithScope scope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      TRACER.end(scope.getSpan(), response);
    }
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    SpanWithScope scope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      TRACER.endExceptionally(scope.getSpan(), response, e);
    }
  }
}
