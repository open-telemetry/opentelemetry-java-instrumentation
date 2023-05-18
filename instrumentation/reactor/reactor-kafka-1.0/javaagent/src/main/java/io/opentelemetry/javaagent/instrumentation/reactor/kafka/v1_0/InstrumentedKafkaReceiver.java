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

  @Override
  public Flux<ReceiverRecord<K, V>> receive() {
    return new InstrumentedKafkaFlux<>(actual.receive());
  }

  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck() {
    return actual.receiveAutoAck().map(InstrumentedKafkaFlux::new);
  }

  @Override
  public Flux<ConsumerRecord<K, V>> receiveAtmostOnce() {
    return new InstrumentedKafkaFlux<>(actual.receiveAtmostOnce());
  }

  @Override
  public Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(
      TransactionManager transactionManager) {
    return actual.receiveAutoAck().map(InstrumentedKafkaFlux::new);
  }

  @Override
  public <T> Mono<T> doOnConsumer(Function<Consumer<K, V>, ? extends T> function) {
    return actual.doOnConsumer(function);
  }
}
