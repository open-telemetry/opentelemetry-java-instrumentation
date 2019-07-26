package datadog.trace.instrumentation.springwebflux.client;

import static datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopSpan;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

public class TracingClientResponseSubscriber implements CoreSubscriber<ClientResponse> {

  private final Tracer tracer = GlobalTracer.get();
  private final CoreSubscriber<? super ClientResponse> subscriber;
  private final ClientRequest clientRequest;
  private final Context context;
  private final AtomicReference<Span> spanRef;
  private final Span parentSpan;

  public TracingClientResponseSubscriber(
    final CoreSubscriber<? super ClientResponse> subscriber,
    final ClientRequest clientRequest,
    final Context context,
    final Span span,
    final Span parentSpan) {
    this.subscriber = subscriber;
    this.clientRequest = clientRequest;
    this.context = context;
    spanRef = new AtomicReference<>(span);
    this.parentSpan = parentSpan == null ? NoopSpan.INSTANCE : parentSpan;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    final Span span = spanRef.get();
    if (span == null) {
      subscriber.onSubscribe(subscription);
      return;
    }

    try (final Scope scope = tracer.scopeManager().activate(span, false)) {

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      DECORATE.onRequest(span, clientRequest);

      subscriber.onSubscribe(
        new Subscription() {
          @Override
          public void request(final long n) {
            try (final Scope scope = tracer.scopeManager().activate(span, false)) {
              subscription.request(n);
            }
          }

          @Override
          public void cancel() {
            DECORATE.onCancel(span);
            DECORATE.beforeFinish(span);
            subscription.cancel();
            span.finish();
          }
        });
    }
  }

  @Override
  public void onNext(final ClientResponse clientResponse) {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onResponse(span, clientResponse);
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final Scope scope = tracer.scopeManager().activate(parentSpan, false)) {

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      subscriber.onNext(clientResponse);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final Scope scope = tracer.scopeManager().activate(parentSpan, false)) {

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    final Span span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final Scope scope = tracer.scopeManager().activate(parentSpan, false)) {

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      subscriber.onComplete();
    }
  }

  @Override
  public Context currentContext() {
    return context;
  }
}
