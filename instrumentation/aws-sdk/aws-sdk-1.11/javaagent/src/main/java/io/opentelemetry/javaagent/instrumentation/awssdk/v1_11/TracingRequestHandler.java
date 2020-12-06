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
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

/** Tracing Request Handler. */
public class TracingRequestHandler extends RequestHandler2 {

  private final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore;

  public TracingRequestHandler(ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void beforeRequest(Request<?> request) {
    AmazonWebServiceRequest originalRequest = request.getOriginalRequest();
    RequestMeta requestMeta = contextStore.get(originalRequest);
    HttpClientOperation<Response<?>> operation = tracer().startOperation(request, requestMeta);
    Scope scope = operation.makeCurrent();
    request.addHandlerContext(
        CONTEXT_SCOPE_PAIR_CONTEXT_KEY, new OperationScopePair(operation, scope));
  }

  @Override
  public void afterResponse(Request<?> request, Response<?> response) {
    OperationScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      scope.getOperation().end(response);
    }
  }

  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    OperationScopePair scope = request.getHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(CONTEXT_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      scope.getOperation().endExceptionally(e);
    }
  }
}
