/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

abstract class AbstractWrapperTest extends KafkaClientBaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final String greeting = "Hello Kafka!";

  @ParameterizedTest
  @CsvSource({
    "true, true",
    "false, false",
  })
  void testWrappers(boolean testHeaders, boolean testExperimental) throws InterruptedException {
    KafkaTelemetryBuilder telemetryBuilder =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("Test-Message-Header"))
            .setCaptureExperimentalSpanAttributes(testExperimental);
    configure(telemetryBuilder);
    KafkaTelemetry telemetry = telemetryBuilder.build();

    Producer<Integer, String> wrappedProducer = telemetry.wrap(producer);

    testing.runWithSpan(
        "parent",
        () -> {
          ProducerRecord<Integer, String> producerRecord =
              new ProducerRecord<>(SHARED_TOPIC, greeting);
          if (testHeaders) {
            producerRecord.headers().add("Test-Message-Header", "test".getBytes(UTF_8));
            producerRecord.headers().add("Uncaptured-Header", "password".getBytes(UTF_8));
          }
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
    Consumer<Integer, String> wrappedConsumer = telemetry.wrap(consumer);
    ConsumerRecords<?, ?> records = wrappedConsumer.poll(Duration.ofSeconds(10));
    assertThat(records.count()).isEqualTo(1);
    for (ConsumerRecord<?, ?> record : records) {
      assertThat(record.value()).isEqualTo(greeting);
      assertThat(record.key()).isNull();
      testing.runWithSpan("process child", () -> {});
    }

    assertTraces(testHeaders, testExperimental);
    assertMessagingMetrics();
  }

  private void assertMessagingMetrics() {
    testing.waitAndAssertMetrics(
        KafkaTelemetryBuilder.INSTRUMENTATION_NAME,
        "messaging.publish.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName("messaging.publish.duration")
                        .hasUnit("s")
                        .hasDescription("Measures the duration of publish operation.")));

    // receive metrics come only from the receive instrumenter (gated by receive telemetry); the
    // process instrumenter does not record them, matching the Pulsar instrumentation
    if (!isReceiveTelemetryEnabled()) {
      return;
    }
    testing.waitAndAssertMetrics(
        KafkaTelemetryBuilder.INSTRUMENTATION_NAME,
        "messaging.receive.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName("messaging.receive.duration")
                        .hasUnit("s")
                        .hasDescription("Measures the duration of receive operation.")));
    testing.waitAndAssertMetrics(
        KafkaTelemetryBuilder.INSTRUMENTATION_NAME,
        "messaging.receive.messages",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasName("messaging.receive.messages")
                        .hasUnit("{message}")
                        .hasDescription("Measures the number of received messages.")));
  }

  boolean isReceiveTelemetryEnabled() {
    return false;
  }

  abstract void configure(KafkaTelemetryBuilder builder);

  abstract void assertTraces(boolean testHeaders, boolean testExperimental);
}
