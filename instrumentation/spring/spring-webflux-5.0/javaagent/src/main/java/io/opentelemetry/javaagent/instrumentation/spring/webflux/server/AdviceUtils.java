/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.WebfluxSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ClassNames;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

public class AdviceUtils {

  public static final String ON_SPAN_END = AdviceUtils.class.getName() + ".Context";

  public static String spanNameForHandler(Object handler) {
    String className = ClassNames.simpleName(handler.getClass());
    int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      return className.substring(0, lambdaIdx) + ".lambda";
    }
    return className + ".handle";
  }

  public static void registerOnSpanEnd(
      ServerWebExchange exchange, Context context, Object handler) {
    exchange
        .getAttributes()
        .put(
            AdviceUtils.ON_SPAN_END,
            (AdviceUtils.OnSpanEnd) t -> instrumenter().end(context, handler, null, t));
  }

  // TODO(trask): synchronize this with WithSpan handling of Mono return values
  public static <T> Mono<T> end(Mono<T> mono, ServerWebExchange exchange) {
    return mono.<T>transform(endOnNextOrError(exchange));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  // TODO(trask): synchronize this with WithSpan handling of Mono return values
  private static <T> Function<? super Publisher<T>, ? extends Publisher<T>> endOnNextOrError(
      ServerWebExchange exchange) {
    return Operators.lift(
        (scannable, subscriber) -> new SpanFinishingSubscriber<>(subscriber, exchange));
  }

  @FunctionalInterface
  interface OnSpanEnd {
    void end(Throwable throwable);
  }

  // TODO(trask): synchronize this with WithSpan handling of Mono return values
  public static class SpanFinishingSubscriber<T> implements CoreSubscriber<T>, Subscription {

    private final CoreSubscriber<? super T> subscriber;
    private final ServerWebExchange exchange;
    private final AtomicBoolean completed = new AtomicBoolean();
    private Subscription subscription;

    public SpanFinishingSubscriber(
        CoreSubscriber<? super T> subscriber, ServerWebExchange exchange) {
      this.subscriber = subscriber;
      this.exchange = exchange;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      this.subscription = subscription;
      subscriber.onSubscribe(this);
    }

    @Override
    public void onNext(T t) {
      subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
      if (completed.compareAndSet(false, true)) {
        endSpan(exchange, t);
      }
      subscriber.onError(t);
    }

    @Override
    public void onComplete() {
      if (completed.compareAndSet(false, true)) {
        endSpan(exchange, null);
      }
      subscriber.onComplete();
    }

    @Override
    public void request(long n) {
      subscription.request(n);
    }

    @Override
    public void cancel() {
      if (completed.compareAndSet(false, true)) {
        endSpan(exchange, null);
      }
      subscription.cancel();
    }

    private static void endSpan(ServerWebExchange exchange, @Nullable Throwable throwable) {
      OnSpanEnd onSpanEnd = (OnSpanEnd) exchange.getAttributes().get(AdviceUtils.ON_SPAN_END);
      if (onSpanEnd != null) {
        onSpanEnd.end(throwable);
      }
    }
  }
}
