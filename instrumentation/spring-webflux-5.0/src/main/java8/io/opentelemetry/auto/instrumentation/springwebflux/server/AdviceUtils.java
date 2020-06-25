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

package io.opentelemetry.auto.instrumentation.springwebflux.server;

import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springwebflux.server.SpringWebfluxHttpServerDecorator.TRACER;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

@Slf4j
public class AdviceUtils {

  public static final String SPAN_ATTRIBUTE =
      "io.opentelemetry.auto.instrumentation.springwebflux.Span";
  public static final String PARENT_SPAN_ATTRIBUTE =
      "io.opentelemetry.auto.instrumentation.springwebflux.ParentSpan";

  public static String parseOperationName(final Object handler) {
    final String className = DECORATE.spanNameForClass(handler.getClass());
    final String operationName;
    final int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      operationName = className.substring(0, lambdaIdx) + ".lambda";
    } else {
      operationName = className + ".handle";
    }
    return operationName;
  }

  public static <T> Mono<T> setPublisherSpan(final Mono<T> mono, final Span span) {
    return mono.<T>transform(finishSpanNextOrError(span));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError(
      final Span span) {
    return Operators.lift(
        (scannable, subscriber) -> new SpanFinishingSubscriber<>(subscriber, span));
  }

  public static void finishSpanIfPresent(
      final ServerWebExchange exchange, final Throwable throwable) {
    if (exchange != null) {
      finishSpanIfPresentInAttributes(exchange.getAttributes(), throwable);
    }
  }

  public static void finishSpanIfPresent(
      final ServerRequest serverRequest, final Throwable throwable) {
    if (serverRequest != null) {
      finishSpanIfPresentInAttributes(serverRequest.attributes(), throwable);
    }
  }

  private static void finishSpanIfPresentInAttributes(
      final Map<String, Object> attributes, final Throwable throwable) {

    final Span span = (Span) attributes.remove(SPAN_ATTRIBUTE);
    finishSpanIfPresent(span, throwable);
  }

  static void finishSpanIfPresent(final Span span, final Throwable throwable) {
    if (span != null) {
      if (throwable != null) {
        span.setStatus(Status.UNKNOWN);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
    }
  }

  public static class SpanFinishingSubscriber<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<? super T> subscriber;
    private final Span span;
    private final Context context;

    public SpanFinishingSubscriber(final CoreSubscriber<? super T> subscriber, final Span span) {
      this.subscriber = subscriber;
      this.span = span;
      context = subscriber.currentContext().put(Span.class, span);
    }

    @Override
    public void onSubscribe(final Subscription s) {
      try (final Scope scope = TRACER.withSpan(span)) {
        subscriber.onSubscribe(s);
      }
    }

    @Override
    public void onNext(final T t) {
      try (final Scope scope = TRACER.withSpan(span)) {
        subscriber.onNext(t);
      }
    }

    @Override
    public void onError(final Throwable t) {
      finishSpanIfPresent(span, t);
      subscriber.onError(t);
    }

    @Override
    public void onComplete() {
      finishSpanIfPresent(span, null);
      subscriber.onComplete();
    }

    @Override
    public Context currentContext() {
      return context;
    }
  }
}
