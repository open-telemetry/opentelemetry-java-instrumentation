/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractWrapperTest extends KafkaClientBaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final String greeting = "Hello Kafka!";

  protected static final boolean MESSAGING_RECEIVE_TELEMETRY_ENABLED =
      Boolean.getBoolean("test.messagingReceiveTelemetryEnabled");
  protected static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Boolean.getBoolean("test.captureExperimentalSpanAttributes");

  protected long consumedOffset;

  @Test
  void testWrappers() throws InterruptedException {
    KafkaTelemetry telemetry = kafkaTelemetryBuilder().build();

    Producer<Integer, String> wrappedProducer = telemetry.instrument(producer);

    testing.runWithSpan(
        "parent",
        () -> {
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, greeting);
          producerRecord.headers().add("Test-Message-Header", "test".getBytes(UTF_8));
          producerRecord.headers().add("Uncaptured-Header", "password".getBytes(UTF_8));
          wrappedProducer.send(
              producerRecord,
              (meta, ex) -> {
                if (ex == null) {
                  testing.runWithSpan("producer callback", () -> {});
                } else {
                  testing.runWithSpan("producer exception: " + ex, () -> {});
                }
              });
        });

    awaitUntilConsumerIsReady();
    Consumer<Integer, String> wrappedConsumer = telemetry.instrument(consumer);
    ConsumerRecords<?, ?> records = wrappedConsumer.poll(Duration.ofSeconds(10));
    assertThat(records.count()).isEqualTo(1);
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
      consumedOffset = record.offset();
      testing.runWithSpan("process child", () -> {});
    }

    assertTraces(true, CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES);
  }

  protected KafkaTelemetryBuilder kafkaTelemetryBuilder() {
    KafkaTelemetryBuilder telemetryBuilder =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(
                IncludeExclude.builder()
                    .setIncluded("Test-Message-*")
                    .setExcluded("*-Excluded-Header")
                    .build());
    if (MESSAGING_RECEIVE_TELEMETRY_ENABLED) {
      telemetryBuilder.setMessagingReceiveTelemetryEnabled(true);
    }
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      telemetryBuilder.setCaptureExperimentalSpanAttributes(true);
    }
    configure(telemetryBuilder);
    return telemetryBuilder;
  }

  void configure(KafkaTelemetryBuilder builder) {}

  abstract void assertTraces(boolean testHeaders, boolean testExperimental);
}
