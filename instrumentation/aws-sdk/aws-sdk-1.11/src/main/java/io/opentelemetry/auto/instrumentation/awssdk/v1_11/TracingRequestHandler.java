/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.awssdk.v1_11;

import static io.opentelemetry.auto.instrumentation.awssdk.v1_11.RequestMeta.SPAN_SCOPE_PAIR_CONTEXT_KEY;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.api.decorator.ClientDecorator.currentContextWith;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {

  private final AwsSdkClientTracer tracer;

  public TracingRequestHandler(
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    tracer = new AwsSdkClientTracer(contextStore);
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  @Override
  public void beforeRequest(final Request<?> request) {
    Span span = tracer.startSpan(request);
    request.addHandlerContext(
        SPAN_SCOPE_PAIR_CONTEXT_KEY,
        new SpanWithScope(span, withScopedContext(currentContextWith(span))));
  }

  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    SpanWithScope scope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      tracer.end(scope.getSpan(), response);
    }
  }

  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    SpanWithScope scope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (scope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      scope.closeScope();
      tracer.endExceptionally(scope.getSpan(), response, e);
    }
  }
}
