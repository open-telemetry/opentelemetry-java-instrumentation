/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import java.util.function.Function;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.TransactionManager;

public final class InstrumentedKafkaReceiver<K, V> implements KafkaReceiver<K, V> {

  private final KafkaReceiver<K, V> actual;

  public InstrumentedKafkaReceiver(KafkaReceiver<K, V> actual) {
    this.actual = actual;
  }

  // added in 1.3.3
  @Override
  public Flux<ReceiverRecord<K, V>> receive(Integer prefetch) {
    return wrap(KafkaReceiver13Access.receive(actual, prefetch));
  }

  @Override
  public Flux<ReceiverRecord<K, V>> receive() {
    return wrap(actual.receive());
  }

  // added in 1.3.3
  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck(Integer prefetch) {
    return KafkaReceiver13Access.receiveAutoAck(actual, prefetch)
        .map(InstrumentedKafkaReceiver::wrap);
  }

  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck() {
    return actual.receiveAutoAck().map(InstrumentedKafkaReceiver::wrap);
  }

  // added in 1.3.3
  @Override
  public Flux<ConsumerRecord<K, V>> receiveAtmostOnce(Integer prefetch) {
    return wrap(KafkaReceiver13Access.receiveAtmostOnce(actual, prefetch));
  }

  @Override
  public Flux<ConsumerRecord<K, V>> receiveAtmostOnce() {
    return wrap(actual.receiveAtmostOnce());
  }

  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(
      TransactionManager transactionManager) {
    return actual.receiveExactlyOnce(transactionManager).map(InstrumentedKafkaReceiver::wrap);
  }

  // added in 1.3.3
  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(
      TransactionManager transactionManager, Integer prefetch) {
    return KafkaReceiver13Access.receiveExactlyOnce(actual, transactionManager, prefetch)
        .map(InstrumentedKafkaReceiver::wrap);
  }

  @Override
  public <T> Mono<T> doOnConsumer(Function<Consumer<K, V>, ? extends T> function) {
    return actual.doOnConsumer(function);
  }

  // added in 1.3.21
  @Override
  public Flux<Flux<ReceiverRecord<K, V>>> receiveBatch(Integer prefetch) {
    return KafkaReceiver13Access.receiveBatch(actual, prefetch)
        .map(InstrumentedKafkaReceiver::wrap);
  }

  // added in 1.3.21
  @Override
  public Flux<Flux<ReceiverRecord<K, V>>> receiveBatch() {
    return KafkaReceiver13Access.receiveBatch(actual).map(InstrumentedKafkaReceiver::wrap);
  }

  private static <K, V, R extends ConsumerRecord<K, V>> Flux<R> wrap(Flux<R> flux) {
    return flux instanceof InstrumentedKafkaFlux ? flux : new InstrumentedKafkaFlux<>(flux);
  }
}
