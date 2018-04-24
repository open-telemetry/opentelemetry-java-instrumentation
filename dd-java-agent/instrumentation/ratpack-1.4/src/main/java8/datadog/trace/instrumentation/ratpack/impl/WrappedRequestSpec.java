package datadog.trace.instrumentation.ratpack.impl;

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

  WrappedRequestSpec(RequestSpec spec, Tracer tracer, Scope scope, AtomicReference<Span> spanRef) {
    this.delegate = spec;
    this.tracer = tracer;
    this.scope = scope;
    this.spanRef = spanRef;
    this.delegate.onRedirect(this::redirectHandler);
  }

  /*
   * Default redirect handler that ensures the span is marked as received before
   * a new span is created.
   *
   */
  private Action<? super RequestSpec> redirectHandler(ReceivedResponse response) {
    //handler.handleReceive(response.getStatusCode(), null, span.get());
    return (s) -> new WrappedRequestSpec(s, tracer, scope, spanRef);
  }

  @Override
  public RequestSpec redirects(int maxRedirects) {
    this.delegate.redirects(maxRedirects);
    return this;
  }

  @Override
  public RequestSpec onRedirect(
      Function<? super ReceivedResponse, Action<? super RequestSpec>> function) {

    Function<? super ReceivedResponse, Action<? super RequestSpec>> wrapped =
        (ReceivedResponse response) -> redirectHandler(response).append(function.apply(response));

    this.delegate.onRedirect(wrapped);
    return this;
  }

  @Override
  public RequestSpec sslContext(SSLContext sslContext) {
    this.delegate.sslContext(sslContext);
    return this;
  }

  @Override
  public MutableHeaders getHeaders() {
    return this.delegate.getHeaders();
  }

  @Override
  public RequestSpec maxContentLength(int numBytes) {
    this.delegate.maxContentLength(numBytes);
    return this;
  }

  @Override
  public RequestSpec headers(Action<? super MutableHeaders> action) throws Exception {
    this.delegate.headers(action);
    return this;
  }

  @Override
  public RequestSpec method(HttpMethod method) {
    Span span =
        tracer
            .buildSpan("ratpack.client-request")
            .asChildOf(scope != null ? scope.span() : null)
            .withTag(Tags.COMPONENT.getKey(), "httpclient")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.HTTP_URL.getKey(), getUri().toString())
            .withTag(Tags.HTTP_METHOD.getKey(), method.getName())
            .start();
    this.spanRef.set(span);
    this.delegate.method(method);
    tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new RequestSpecInjectAdapter(this));
    return this;
  }

  @Override
  public RequestSpec decompressResponse(boolean shouldDecompress) {
    this.delegate.decompressResponse(shouldDecompress);
    return this;
  }

  @Override
  public URI getUri() {
    return this.delegate.getUri();
  }

  @Override
  public RequestSpec connectTimeout(Duration duration) {
    this.delegate.connectTimeout(duration);
    return this;
  }

  @Override
  public RequestSpec readTimeout(Duration duration) {
    this.delegate.readTimeout(duration);
    return this;
  }

  @Override
  public Body getBody() {
    return this.delegate.getBody();
  }

  @Override
  public RequestSpec body(Action<? super Body> action) throws Exception {
    this.delegate.body(action);
    return this;
  }
}
