/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.reactor;

import static reactor.core.publisher.Operators.lift;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
public class ReactorCoreAdviceUtils {

  public static final String PUBLISHER_CONTEXT_KEY =
      "io.opentelemetry.auto.instrumentation.reactor.Span";

  public static <T> Mono<T> setPublisherSpan(final Mono<T> mono, final Span span) {
    return mono.<T>transform(finishSpanNextOrError())
        .subscriberContext(Context.of(PUBLISHER_CONTEXT_KEY, span));
  }

  public static <T> Flux<T> setPublisherSpan(final Flux<T> flux, final Span span) {
    return flux.<T>transform(finishSpanNextOrError())
        .subscriberContext(Context.of(PUBLISHER_CONTEXT_KEY, span));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T, IP>
      Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError() {
    return lift((scannable, subscriber) -> new TracingSubscriber<>(subscriber));
  }

  public static void finishSpanIfPresent(final Context context, final Throwable throwable) {
    finishSpanIfPresent(context.getOrDefault(PUBLISHER_CONTEXT_KEY, (Span) null), throwable);
  }

  public static void finishSpanIfPresent(final Span span, final Throwable throwable) {
    if (span != null) {
      if (throwable != null) {
        span.setStatus(Status.UNKNOWN);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
    }
  }

  public static class TracingSubscriber<T> implements CoreSubscriber<T> {

    private final Context context;
    private final CoreSubscriber<? super T> subscriber;

    public TracingSubscriber(final CoreSubscriber<? super T> subscriber) {
      this.subscriber = subscriber;
      context = subscriber.currentContext();
    }

    @Override
    public void onNext(final T event) {
      subscriber.onNext(event);
    }

    @Override
    public void onError(final Throwable throwable) {
      finishSpanIfPresent(context, throwable);
      subscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
      finishSpanIfPresent(context, null);
      subscriber.onComplete();
    }

    @Override
    public Context currentContext() {
      return context;
    }

    @Override
    public void onSubscribe(final Subscription s) {
      subscriber.onSubscribe(s);
    }
  }
}
