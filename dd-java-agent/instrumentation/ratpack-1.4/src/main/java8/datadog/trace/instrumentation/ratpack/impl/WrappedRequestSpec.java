package datadog.trace.instrumentation.ratpack.impl;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import ratpack.func.Action;
import ratpack.func.Function;
import ratpack.http.HttpMethod;
import ratpack.http.MutableHeaders;
import ratpack.http.client.ReceivedResponse;
import ratpack.http.client.RequestSpec;

/**
 * RequestSpec wrapper that captures the method type, sets up redirect handling and starts new spans
 * when a method type is set.
 */
public final class WrappedRequestSpec implements RequestSpec {

  private final RequestSpec delegate;
  private final Tracer tracer;
  private final Scope scope;
  private final AtomicReference<Span> spanRef;

  WrappedRequestSpec(
      final RequestSpec spec,
      final Tracer tracer,
      final Scope scope,
      final AtomicReference<Span> spanRef) {
    delegate = spec;
    this.tracer = tracer;
    this.scope = scope;
    this.spanRef = spanRef;
    delegate.onRedirect(this::redirectHandler);
  }

  /*
   * Default redirect handler that ensures the span is marked as received before
   * a new span is created.
   *
   */
  private Action<? super RequestSpec> redirectHandler(final ReceivedResponse response) {
    // handler.handleReceive(response.getStatusCode(), null, span.get());
    return (s) -> new WrappedRequestSpec(s, tracer, scope, spanRef);
  }

  @Override
  public RequestSpec redirects(final int maxRedirects) {
    delegate.redirects(maxRedirects);
    return this;
  }

  @Override
  public RequestSpec onRedirect(
      final Function<? super ReceivedResponse, Action<? super RequestSpec>> function) {

    final Function<? super ReceivedResponse, Action<? super RequestSpec>> wrapped =
        (ReceivedResponse response) -> redirectHandler(response).append(function.apply(response));

    delegate.onRedirect(wrapped);
    return this;
  }

  @Override
  public RequestSpec sslContext(final SSLContext sslContext) {
    delegate.sslContext(sslContext);
    return this;
  }

  @Override
  public MutableHeaders getHeaders() {
    return delegate.getHeaders();
  }

  @Override
  public RequestSpec maxContentLength(final int numBytes) {
    delegate.maxContentLength(numBytes);
    return this;
  }

  @Override
  public RequestSpec headers(final Action<? super MutableHeaders> action) throws Exception {
    delegate.headers(action);
    return this;
  }

  @Override
  public RequestSpec method(final HttpMethod method) {
    final Span span =
        tracer
            .buildSpan("ratpack.client-request")
            .asChildOf(scope != null ? scope.span() : null)
            .withTag(Tags.COMPONENT.getKey(), "ratpack-httpclient")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
            .withTag(Tags.HTTP_URL.getKey(), getUri().toString())
            .withTag(Tags.HTTP_METHOD.getKey(), method.getName())
            .start();
    spanRef.set(span);
    delegate.method(method);
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestSpecInjectAdapter(this));
    return this;
  }

  @Override
  public RequestSpec decompressResponse(final boolean shouldDecompress) {
    delegate.decompressResponse(shouldDecompress);
    return this;
  }

  @Override
  public URI getUri() {
    return delegate.getUri();
  }

  @Override
  public RequestSpec connectTimeout(final Duration duration) {
    delegate.connectTimeout(duration);
    return this;
  }

  @Override
  public RequestSpec readTimeout(final Duration duration) {
    delegate.readTimeout(duration);
    return this;
  }

  @Override
  public Body getBody() {
    return delegate.getBody();
  }

  @Override
  public RequestSpec body(final Action<? super Body> action) throws Exception {
    delegate.body(action);
    return this;
  }
}
