/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaProcessingSelectionUtil.selectFrameworkProcessing;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

public class ProcessingSelectingKafkaFlux
    extends FluxOperator<ConsumerRecords<?, ?>, ConsumerRecords<?, ?>> {

  public ProcessingSelectingKafkaFlux(Flux<? extends ConsumerRecords<?, ?>> source) {
    super(source);
  }

  @Override
  public void subscribe(CoreSubscriber<? super ConsumerRecords<?, ?>> actual) {
    source.subscribe(new ProcessingSelectingSubscriber(actual));
  }

  static final class ProcessingSelectingSubscriber
      implements CoreSubscriber<ConsumerRecords<?, ?>>, Subscription, Scannable {

    private final CoreSubscriber<? super ConsumerRecords<?, ?>> actual;
    private Subscription subscription;

    ProcessingSelectingSubscriber(CoreSubscriber<? super ConsumerRecords<?, ?>> actual) {
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
    public Context currentContext() {
      return actual.currentContext();
    }

    @Override
    public void onNext(ConsumerRecords<?, ?> records) {
      selectFrameworkProcessing(records);
      actual.onNext(records);
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
