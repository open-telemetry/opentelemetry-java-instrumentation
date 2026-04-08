/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
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

  @Test
  @SuppressWarnings({"unchecked"})
  void testProducerHandlesReadOnlyHeaders() {
    Producer<String, String> producer = mock(Producer.class);
    when(producer.send(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

    KafkaTelemetry telemetry = KafkaTelemetry.builder(testing.getOpenTelemetry()).build();
    Producer<String, String> wrappedProducer = telemetry.wrap(producer);

    ProducerRecord<String, String> record =
        new ProducerRecord<>(
            "test-topic", null, null, "test-key", "test-value", new RecordHeaders());
    ((RecordHeaders) record.headers()).setReadOnly();
    assertThatNoException()
        .isThrownBy(() -> testing.runWithSpan("parent", () -> wrappedProducer.send(record)));
  }
}
