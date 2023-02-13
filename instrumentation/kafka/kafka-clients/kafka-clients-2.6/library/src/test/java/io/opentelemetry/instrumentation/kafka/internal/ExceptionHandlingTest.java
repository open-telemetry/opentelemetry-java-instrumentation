/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.kafkaclients.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExceptionHandlingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void testConsumerExceptionPropagatesToCaller() {
    Consumer<?, ?> consumer =
        (Consumer<?, ?>)
            Proxy.newProxyInstance(
                ExceptionHandlingTest.class.getClassLoader(),
                new Class<?>[] {Consumer.class},
                (proxy, method, args) -> {
                  throw new IllegalStateException("can't invoke");
                });

    KafkaTelemetry telemetry = KafkaTelemetry.builder(testing.getOpenTelemetry()).build();
    Consumer<?, ?> wrappedConsumer = telemetry.wrap(consumer);

    assertThatThrownBy(() -> wrappedConsumer.poll(Duration.ofMillis(1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("can't invoke");
  }

  @Test
  void testProducerExceptionPropagatesToCaller() {
    Producer<?, ?> producer =
        (Producer<?, ?>)
            Proxy.newProxyInstance(
                ExceptionHandlingTest.class.getClassLoader(),
                new Class<?>[] {Producer.class},
                (proxy, method, args) -> {
                  throw new IllegalStateException("can't invoke");
                });

    KafkaTelemetry telemetry = KafkaTelemetry.builder(testing.getOpenTelemetry()).build();
    Producer<?, ?> wrappedProducer = telemetry.wrap(producer);
    assertThatThrownBy(wrappedProducer::flush)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("can't invoke");
  }
}
