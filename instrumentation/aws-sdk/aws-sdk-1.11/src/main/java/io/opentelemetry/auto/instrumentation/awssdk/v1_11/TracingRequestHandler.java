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

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.instrumentation.api.decorator.ClientDecorator;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.aws-sdk-1.11");

  private final AwsSdkClientDecorator decorate;

  public TracingRequestHandler(
      final ContextStore<AmazonWebServiceRequest, RequestMeta> contextStore) {
    decorate = new AwsSdkClientDecorator(contextStore);
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  @Override
  public void beforeRequest(final Request<?> request) {
    Span span = decorate.getOrCreateSpan(request, TRACER);
    decorate.afterStart(span);
    decorate.onRequest(span, request);
    request.addHandlerContext(
        SPAN_SCOPE_PAIR_CONTEXT_KEY,
        new SpanWithScope(
            span, ContextUtils.withScopedContext(ClientDecorator.currentContextWith(span))));
  }

  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    SpanWithScope spanWithScope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanWithScope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanWithScope.closeScope();
      Span span = spanWithScope.getSpan();
      decorate.onResponse(span, response);
      decorate.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    SpanWithScope spanWithScope = request.getHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY);
    if (spanWithScope != null) {
      request.addHandlerContext(SPAN_SCOPE_PAIR_CONTEXT_KEY, null);
      spanWithScope.closeScope();
      Span span = spanWithScope.getSpan();
      decorate.onError(span, e);
      decorate.beforeFinish(span);
      span.end();
    }
  }
}
