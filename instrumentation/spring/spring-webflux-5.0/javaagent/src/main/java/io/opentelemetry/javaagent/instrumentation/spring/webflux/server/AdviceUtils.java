/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.SpringWebfluxHttpServerTracer.TRACER;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.StatusCode;
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
      "io.opentelemetry.javaagent.instrumentation.springwebflux.Context";

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

  public static <T> Mono<T> setPublisherSpan(
      Mono<T> mono, io.opentelemetry.context.Context context) {
    return mono.<T>transform(finishSpanNextOrError(context));
  }

  /**
   * Idea for this has been lifted from https://github.com/reactor/reactor-core/issues/947. Newer
   * versions of reactor-core have easier way to access context but we want to support older
   * versions.
   */
  public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> finishSpanNextOrError(
      io.opentelemetry.context.Context context) {
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

    io.opentelemetry.context.Context context =
        (io.opentelemetry.context.Context) attributes.remove(CONTEXT_ATTRIBUTE);
    finishSpanIfPresent(context, throwable);
  }

  static void finishSpanIfPresent(io.opentelemetry.context.Context context, Throwable throwable) {
    if (context != null) {
      Span span = TracingContextUtils.getSpan(context);
      if (throwable != null) {
        span.setStatus(StatusCode.ERROR);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
    }
  }

  public static class SpanFinishingSubscriber<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<? super T> subscriber;
    private final io.opentelemetry.context.Context otelContext;
    private final Context context;

    public SpanFinishingSubscriber(
        CoreSubscriber<? super T> subscriber, io.opentelemetry.context.Context otelContext) {
      this.subscriber = subscriber;
      this.otelContext = otelContext;
      context = subscriber.currentContext().put(Span.class, otelContext);
    }

    @Override
    public void onSubscribe(Subscription s) {
      try (Scope scope = otelContext.makeCurrent()) {
        subscriber.onSubscribe(s);
      }
    }

    @Override
    public void onNext(T t) {
      try (Scope scope = otelContext.makeCurrent()) {
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
