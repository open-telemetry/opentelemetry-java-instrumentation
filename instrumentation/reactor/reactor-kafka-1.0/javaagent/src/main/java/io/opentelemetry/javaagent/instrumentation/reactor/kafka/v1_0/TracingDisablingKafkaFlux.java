/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.Operators;

public final class TracingDisablingKafkaFlux<T> extends FluxOperator<T, T> {

  public TracingDisablingKafkaFlux(Flux<? extends T> source) {
    super(source);
  }

  @Override
  public void subscribe(CoreSubscriber<? super T> actual) {
    source.subscribe(new TracingDisablingSubscriber<>(actual));
  }

  static final class TracingDisablingSubscriber<T>
      implements CoreSubscriber<T>, Subscription, Scannable {

    private final CoreSubscriber<T> actual;
    private Subscription subscription;

    TracingDisablingSubscriber(CoreSubscriber<T> actual) {
      this.actual = actual;
    }

    @Override
    public void onSubscribe(Subscription s) {
      if (Operators.validate(this.subscription, s)) {
        this.subscription = s;

        actual.onSubscribe(this);
      }
    }

    @Override
    public reactor.util.context.Context currentContext() {
      return actual.currentContext();
    }

    @Override
    public void onNext(T record) {
      boolean previous = KafkaClientsConsumerProcessTracing.setEnabled(false);
      try {
        actual.onNext(record);
      } finally {
        KafkaClientsConsumerProcessTracing.setEnabled(previous);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      actual.onError(throwable);
    }

    @Override
    public void onComplete() {
      actual.onComplete();
    }

    @Override
    public void request(long l) {
      subscription.request(l);
    }

    @Override
    public void cancel() {
      subscription.cancel();
    }

    @SuppressWarnings("rawtypes") // that's how the method is defined
    @Override
    public Object scanUnsafe(Attr key) {
      if (key == Attr.ACTUAL) {
        return actual;
      }
      if (key == Attr.PARENT) {
        return subscription;
      }
      return null;
    }
  }
}
