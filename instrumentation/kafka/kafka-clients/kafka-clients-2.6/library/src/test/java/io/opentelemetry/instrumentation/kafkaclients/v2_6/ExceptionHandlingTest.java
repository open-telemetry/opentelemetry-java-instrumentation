/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;

class ExceptionHandlingTest extends KafkaClientBaseTest {

  @Test
  void testConsumerPropagatesException() {
    Consumer<?, ?> throwingConsumer =
        (Consumer<?, ?>)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Consumer.class},
                new InvocationHandler() {
                  @Override
                  public Object invoke(Object proxy, Method method, Object[] args) {
                    throw new IllegalStateException("Test exception");
                  }
                });
    Consumer<?, ?> wrappedConsumer =
        KafkaTelemetry.create(testing.getOpenTelemetry()).wrap(throwingConsumer);
    assertThatThrownBy(() -> wrappedConsumer.poll(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Test exception");
  }

  @Test
  void testProducerPropagatesException() {
    Producer<?, ?> throwingProducer =
        (Producer<?, ?>)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Producer.class},
                new InvocationHandler() {
                  @Override
                  public Object invoke(Object proxy, Method method, Object[] args) {
                    throw new IllegalStateException("Test exception");
                  }
                });
    Producer<?, ?> wrappedProducer =
        KafkaTelemetry.create(testing.getOpenTelemetry()).wrap(throwingProducer);
    assertThatThrownBy(() -> wrappedProducer.send(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Test exception");
  }
}
