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
import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TracingContextUtils;
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

  public static final String CONTEXT_ATTRIBUTE =
      "io.opentelemetry.auto.instrumentation.springwebflux.Context";
  public static final String PARENT_CONTEXT_ATTRIBUTE =
      "io.opentelemetry.auto.instrumentation.springwebflux.ParentContext";

  public static String parseOperationName(final Object handler) {
    String className = DECORATE.spanNameForClass(handler.getClass());
    String operationName;
    int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      operationName = className.substring(0, lambdaIdx) + ".lambda";
    } else {
      operationName = className + ".handle";
    }
    return operationName;
  }

  public static <T> Mono<T> setPublisherSpan(final Mono<T> mono, final io.grpc.Context context) {
    return mono.<T>transform(finishSpanNextOrError(context));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError(
      final io.grpc.Context context) {
    return Operators.lift(
        (scannable, subscriber) -> new SpanFinishingSubscriber<>(subscriber, context));
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

    io.grpc.Context context = (io.grpc.Context) attributes.remove(CONTEXT_ATTRIBUTE);
    finishSpanIfPresent(context, throwable);
  }

  static void finishSpanIfPresent(final io.grpc.Context context, final Throwable throwable) {
    if (context != null) {
      Span span = TracingContextUtils.getSpan(context);
      if (throwable != null) {
        span.setStatus(Status.UNKNOWN);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
    }
  }

  public static class SpanFinishingSubscriber<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<? super T> subscriber;
    private final io.grpc.Context otelContext;
    private final Context context;

    public SpanFinishingSubscriber(
        final CoreSubscriber<? super T> subscriber, final io.grpc.Context otelContext) {
      this.subscriber = subscriber;
      this.otelContext = otelContext;
      context = subscriber.currentContext().put(Span.class, otelContext);
    }

    @Override
    public void onSubscribe(final Subscription s) {
      try (Scope scope = withScopedContext(otelContext)) {
        subscriber.onSubscribe(s);
      }
    }

    @Override
    public void onNext(final T t) {
      try (Scope scope = withScopedContext(otelContext)) {
        subscriber.onNext(t);
      }
    }

    @Override
    public void onError(final Throwable t) {
      finishSpanIfPresent(otelContext, t);
      subscriber.onError(t);
    }

    @Override
    public void onComplete() {
      finishSpanIfPresent(otelContext, null);
      subscriber.onComplete();
    }

    @Override
    public Context currentContext() {
      return context;
    }
  }
}
