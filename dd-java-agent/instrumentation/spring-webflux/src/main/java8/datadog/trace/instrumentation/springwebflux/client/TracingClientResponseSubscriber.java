package datadog.trace.instrumentation.springwebflux.client;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.noopSpan;
import static datadog.trace.instrumentation.springwebflux.client.SpringWebfluxHttpClientDecorator.DECORATE;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

public class TracingClientResponseSubscriber implements CoreSubscriber<ClientResponse> {

  private final CoreSubscriber<? super ClientResponse> subscriber;
  private final ClientRequest clientRequest;
  private final Context context;
  private final AtomicReference<AgentSpan> spanRef;
  private final AgentSpan parentSpan;

  public TracingClientResponseSubscriber(
      final CoreSubscriber<? super ClientResponse> subscriber,
      final ClientRequest clientRequest,
      final Context context,
      final AgentSpan span,
      final AgentSpan parentSpan) {
    this.subscriber = subscriber;
    this.clientRequest = clientRequest;
    this.context = context;
    spanRef = new AtomicReference<>(span);
    this.parentSpan = parentSpan == null ? noopSpan() : parentSpan;
  }

  @Override
  public void onSubscribe(final Subscription subscription) {
    final AgentSpan span = spanRef.get();
    if (span == null) {
      subscriber.onSubscribe(subscription);
      return;
    }

    try (final AgentScope scope = activateSpan(span, false)) {

      scope.setAsyncPropagation(true);

      DECORATE.onRequest(span, clientRequest);

      subscriber.onSubscribe(
        new Subscription() {
          @Override
          public void request(final long n) {
            try (final AgentScope scope = activateSpan(span, false)) {
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
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onResponse(span, clientResponse);
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final AgentScope scope = activateSpan(parentSpan, false)) {

      scope.setAsyncPropagation(true);

      subscriber.onNext(clientResponse);
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final AgentScope scope = activateSpan(parentSpan, false)) {

      scope.setAsyncPropagation(true);

      subscriber.onError(throwable);
    }
  }

  @Override
  public void onComplete() {
    final AgentSpan span = spanRef.getAndSet(null);
    if (span != null) {
      DECORATE.beforeFinish(span);
      span.finish();
    }

    try (final AgentScope scope = activateSpan(parentSpan, false)) {

      scope.setAsyncPropagation(true);

      subscriber.onComplete();
    }
  }

  @Override
  public Context currentContext() {
    return context;
  }
}
