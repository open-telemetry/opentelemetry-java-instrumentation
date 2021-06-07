/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReactorAsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("reactor.canceled");

  public static ReactorAsyncSpanEndStrategy create() {
    return newBuilder().build();
  }

  public static ReactorAsyncSpanEndStrategyBuilder newBuilder() {
    return new ReactorAsyncSpanEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  ReactorAsyncSpanEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == Publisher.class || returnType == Mono.class || returnType == Flux.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {

    EndOnFirstNotificationConsumer notificationConsumer =
        new EndOnFirstNotificationConsumer(tracer, context);
    if (returnValue instanceof Mono) {
      Mono<?> mono = (Mono<?>) returnValue;
      return mono.doOnError(notificationConsumer)
          .doOnSuccess(notificationConsumer::onSuccess)
          .doOnCancel(notificationConsumer::onCancel);
    } else {
      Flux<?> flux = Flux.from((Publisher<?>) returnValue);
      return flux.doOnError(notificationConsumer)
          .doOnComplete(notificationConsumer)
          .doOnCancel(notificationConsumer::onCancel);
    }
  }

  /**
   * Helper class to ensure that the span is ended exactly once regardless of how many OnComplete or
   * OnError notifications are received. Multiple notifications can happen anytime multiple
   * subscribers subscribe to the same publisher.
   */
  private final class EndOnFirstNotificationConsumer extends AtomicBoolean
      implements Runnable, Consumer<Throwable> {

    private final BaseTracer tracer;
    private final Context context;

    public EndOnFirstNotificationConsumer(BaseTracer tracer, Context context) {
      super(false);
      this.tracer = tracer;
      this.context = context;
    }

    public <T> void onSuccess(T ignored) {
      accept(null);
    }

    public void onCancel() {
      if (compareAndSet(false, true)) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        tracer.end(context);
      }
    }

    @Override
    public void run() {
      accept(null);
    }

    @Override
    public void accept(Throwable exception) {
      if (compareAndSet(false, true)) {
        if (exception != null) {
          tracer.endExceptionally(context, exception);
        } else {
          tracer.end(context);
        }
      }
    }
  }
}
