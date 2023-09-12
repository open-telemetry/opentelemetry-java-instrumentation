/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.TransactionManager;

final class KafkaReceiver13Access {

  @NoMuzzle
  static <K, V> Flux<ReceiverRecord<K, V>> receive(KafkaReceiver<K, V> receiver, Integer prefetch) {
    return receiver.receive(prefetch);
  }

  @NoMuzzle
  static <K, V> Flux<Flux<ConsumerRecord<K, V>>> receiveAutoAck(
      KafkaReceiver<K, V> receiver, Integer prefetch) {
    return receiver.receiveAutoAck(prefetch);
  }

  @NoMuzzle
  static <K, V> Flux<ConsumerRecord<K, V>> receiveAtmostOnce(
      KafkaReceiver<K, V> receiver, Integer prefetch) {
    return receiver.receiveAtmostOnce(prefetch);
  }

  @NoMuzzle
  static <K, V> Flux<Flux<ConsumerRecord<K, V>>> receiveExactlyOnce(
      KafkaReceiver<K, V> receiver, TransactionManager transactionManager, Integer prefetch) {
    return receiver.receiveExactlyOnce(transactionManager, prefetch);
  }

  @NoMuzzle
  static <K, V> Flux<Flux<ReceiverRecord<K, V>>> receiveBatch(
      KafkaReceiver<K, V> receiver, Integer prefetch) {
    return receiver.receiveBatch(prefetch);
  }

  @NoMuzzle
  static <K, V> Flux<Flux<ReceiverRecord<K, V>>> receiveBatch(KafkaReceiver<K, V> receiver) {
    return receiver.receiveBatch();
  }

  private KafkaReceiver13Access() {}
}
