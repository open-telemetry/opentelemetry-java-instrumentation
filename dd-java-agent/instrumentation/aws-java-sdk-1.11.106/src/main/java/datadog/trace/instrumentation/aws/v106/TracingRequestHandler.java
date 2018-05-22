/*
 * Copyright 2017-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package datadog.trace.instrumentation.aws.v106;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {

  private final HandlerContextKey<Span> contextKey = new HandlerContextKey<>("span");
  private final SpanContext parentContext; // for Async Client
  private final Tracer tracer;

  public TracingRequestHandler(final Tracer tracer) {
    this.parentContext = null;
    this.tracer = tracer;
  }

  /**
   * In case of Async Client: beforeRequest runs in separate thread therefore we need to inject
   * parent context to build chain
   *
   * @param parentContext parent context
   */
  public TracingRequestHandler(final SpanContext parentContext, final Tracer tracer) {
    this.parentContext = parentContext;
    this.tracer = tracer;
  }

  @Override
  public AmazonWebServiceRequest beforeMarshalling(final AmazonWebServiceRequest request) {
    return request;
  }

  /** {@inheritDoc} */
  @Override
  public void beforeRequest(final Request<?> request) {
    final Tracer.SpanBuilder spanBuilder =
        tracer.buildSpan("aws.command").withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    if (parentContext != null) {
      spanBuilder.asChildOf(parentContext);
    }

    final Span span = spanBuilder.start();
    SpanDecorator.onRequest(request, span);

    tracer.inject(
        span.context(),
        Format.Builtin.HTTP_HEADERS,
        new TextMapInjectAdapter(request.getHeaders()));

    request.addHandlerContext(contextKey, span);
  }

  /** {@inheritDoc} */
  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    final Span span = request.getHandlerContext(contextKey);
    SpanDecorator.onResponse(response, span);
    span.finish();
  }

  /** {@inheritDoc} */
  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    final Span span = request.getHandlerContext(contextKey);
    SpanDecorator.onError(e, span);
    span.finish();
  }
}
