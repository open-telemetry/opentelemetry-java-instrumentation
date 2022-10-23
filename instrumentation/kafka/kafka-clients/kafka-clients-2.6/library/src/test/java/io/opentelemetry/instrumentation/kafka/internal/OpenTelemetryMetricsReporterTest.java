/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.kafkaclients.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryMetricsReporterTest extends AbstractOpenTelemetryMetricsReporterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Map<String, ?> additionalConfig() {
    return KafkaTelemetry.create(testing.getOpenTelemetry()).metricConfigProperties();
  }

  @Test
  void badConfig() {
    // Bad producer config
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.remove(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE);
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Missing required configuration property: opentelemetry.instance");
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE, "foo");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.instance is not instance of OpenTelemetry");
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.remove(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME);
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Missing required configuration property: opentelemetry.instrumentation_name");
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME, 42);
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.instrumentation_name is not instance of String");

    // Bad consumer config
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.remove(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE);
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Missing required configuration property: opentelemetry.instance");
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE, "foo");
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.instance is not instance of OpenTelemetry");
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.remove(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME);
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Missing required configuration property: opentelemetry.instrumentation_name");
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME, 42);
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.instrumentation_name is not instance of String");
  }
}
