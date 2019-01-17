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
package datadog.trace.instrumentation.aws.v0;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.Scope;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapInjectAdapter;
import io.opentracing.tag.Tags;

/** Tracing Request Handler */
public class TracingRequestHandler extends RequestHandler2 {

  // Note: aws1.x sdk doesn't have any truly async clients so we can store scope in request context
  // safely.
  private static final HandlerContextKey<Scope> SCOPE_CONTEXT_KEY =
      new HandlerContextKey<>("DatadogScope");

  private final SpanContext parentContext; // for Async Client
  private final Tracer tracer;

  public TracingRequestHandler(final Tracer tracer) {
    parentContext = null;
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
    // Note: not setting Component tag here because it is always set by SpanDecorator
    final Tracer.SpanBuilder spanBuilder =
        tracer.buildSpan("aws.command").withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);

    if (parentContext != null) {
      spanBuilder.asChildOf(parentContext);
    }

    final Scope scope = spanBuilder.startActive(true);
    SpanDecorator.onRequest(request, scope.span());

    // We inject headers at aws-client level because aws requests may be signed and adding headers
    // on http-client level may break signature.
    tracer.inject(
        scope.span().context(),
        Format.Builtin.HTTP_HEADERS,
        new TextMapInjectAdapter(request.getHeaders()));

    request.addHandlerContext(SCOPE_CONTEXT_KEY, scope);
  }

  /** {@inheritDoc} */
  @Override
  public void afterResponse(final Request<?> request, final Response<?> response) {
    final Scope scope = request.getHandlerContext(SCOPE_CONTEXT_KEY);
    SpanDecorator.onResponse(response, scope.span());
    scope.close();
  }

  /** {@inheritDoc} */
  @Override
  public void afterError(final Request<?> request, final Response<?> response, final Exception e) {
    final Scope scope = request.getHandlerContext(SCOPE_CONTEXT_KEY);
    SpanDecorator.onError(e, scope.span());
    scope.close();
  }
}
