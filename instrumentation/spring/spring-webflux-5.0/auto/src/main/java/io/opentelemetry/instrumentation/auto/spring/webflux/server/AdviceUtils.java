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

package io.opentelemetry.instrumentation.auto.spring.webflux.server;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.spring.webflux.server.SpringWebfluxHttpServerTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Map;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public class AdviceUtils {

  public static final String CONTEXT_ATTRIBUTE =
      "io.opentelemetry.instrumentation.auto.springwebflux.Context";

  public static String parseOperationName(Object handler) {
    String className = TRACER.spanNameForClass(handler.getClass());
    String operationName;
    int lambdaIdx = className.indexOf("$$Lambda$");

    if (lambdaIdx > -1) {
      operationName = className.substring(0, lambdaIdx) + ".lambda";
    } else {
      operationName = className + ".handle";
    }
    return operationName;
  }

  public static <T> Mono<T> setPublisherSpan(Mono<T> mono, io.grpc.Context context) {
    return mono.<T>transform(finishSpanNextOrError(context));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError(
      io.grpc.Context context) {
    return Operators.lift(
        (scannable, subscriber) -> new SpanFinishingSubscriber<>(subscriber, context));
  }

  public static void finishSpanIfPresent(ServerWebExchange exchange, Throwable throwable) {
    if (exchange != null) {
      finishSpanIfPresentInAttributes(exchange.getAttributes(), throwable);
    }
  }

  public static void finishSpanIfPresent(ServerRequest serverRequest, Throwable throwable) {
    if (serverRequest != null) {
      finishSpanIfPresentInAttributes(serverRequest.attributes(), throwable);
    }
  }

  private static void finishSpanIfPresentInAttributes(
      Map<String, Object> attributes, Throwable throwable) {

    io.grpc.Context context = (io.grpc.Context) attributes.remove(CONTEXT_ATTRIBUTE);
    finishSpanIfPresent(context, throwable);
  }

  static void finishSpanIfPresent(io.grpc.Context context, Throwable throwable) {
    if (context != null) {
      Span span = TracingContextUtils.getSpan(context);
      if (throwable != null) {
        span.setStatus(Status.ERROR);
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
        CoreSubscriber<? super T> subscriber, io.grpc.Context otelContext) {
      this.subscriber = subscriber;
      this.otelContext = otelContext;
      context = subscriber.currentContext().put(Span.class, otelContext);
    }

    @Override
    public void onSubscribe(Subscription s) {
      try (Scope scope = withScopedContext(otelContext)) {
        subscriber.onSubscribe(s);
      }
    }

    @Override
    public void onNext(T t) {
      try (Scope scope = withScopedContext(otelContext)) {
        subscriber.onNext(t);
      }
    }

    @Override
    public void onError(Throwable t) {
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
