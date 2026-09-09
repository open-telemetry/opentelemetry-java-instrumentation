/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenTelemetryProducerInterceptorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static Map<String, Object> producerConfig() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    config.putAll(
        KafkaTelemetry.create(testing.getOpenTelemetry()).producerInterceptorConfigProperties());
    return config;
  }

  @Test
  void badConfig() {
    // Bad config - wrong type for supplier
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER,
                  "foo");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-producer-telemetry.supplier is not instance of KafkaProducerTelemetrySupplier");

    // Bad config - supplier returns wrong type
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER,
                  (Supplier<?>) () -> "not a KafkaProducerTelemetry");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-producer-telemetry.supplier is not instance of KafkaProducerTelemetrySupplier");
  }

  @Test
  void serializableConfig() throws Exception {
    SerializationTestUtil.testSerialize(
        producerConfig(),
        OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER);
  }

  @ParameterizedTest
  @CsvSource({"true, false", "true, true", "false, false", "false, true"})
  void sendSpanAndContextPropagation(boolean propagationEnabled, boolean readOnlyHeaders) {
    KafkaTelemetry telemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setPropagationEnabled(propagationEnabled)
            .build();
    OpenTelemetryProducerInterceptor<String, String> interceptor =
        new OpenTelemetryProducerInterceptor<>();
    interceptor.configure(telemetry.producerInterceptorConfigProperties());

    ProducerRecord<String, String> record = new ProducerRecord<>("test", 1, 123L, "key", "value");
    record.headers().add("custom", "header".getBytes(UTF_8));
    if (readOnlyHeaders) {
      ((RecordHeaders) record.headers()).setReadOnly();
    }

    ProducerRecord<String, String> tracedRecord = interceptor.onSend(record);

    if (propagationEnabled && readOnlyHeaders) {
      assertThat(tracedRecord).isNotSameAs(record);
      assertThat(record.headers().headers("traceparent")).isEmpty();
    } else {
      assertThat(tracedRecord).isSameAs(record);
    }
    assertThat(tracedRecord.topic()).isEqualTo("test");
    assertThat(tracedRecord.partition()).isEqualTo(1);
    assertThat(tracedRecord.timestamp()).isEqualTo(123L);
    assertThat(tracedRecord.key()).isEqualTo("key");
    assertThat(tracedRecord.value()).isEqualTo("value");
    assertThat(tracedRecord.headers().headers("custom"))
        .singleElement()
        .satisfies(header -> assertThat(header.value()).isEqualTo("header".getBytes(UTF_8)));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "send test" : "test publish")
                        .hasKind(
                            emitStableMessagingSemconv() && !propagationEnabled
                                ? SpanKind.CLIENT
                                : SpanKind.PRODUCER)
                        .hasNoParent()
                        .satisfies(
                            spanData -> {
                              if (propagationEnabled) {
                                assertThat(tracedRecord.headers().headers("traceparent"))
                                    .singleElement()
                                    .satisfies(
                                        header ->
                                            assertThat(new String(header.value(), UTF_8))
                                                .isEqualTo(
                                                    "00-"
                                                        + spanData.getTraceId()
                                                        + "-"
                                                        + spanData.getSpanId()
                                                        + "-"
                                                        + spanData
                                                            .getSpanContext()
                                                            .getTraceFlags()
                                                            .asHex()));
                              } else {
                                assertThat(tracedRecord.headers().headers("traceparent")).isEmpty();
                              }
                            })));
  }
}
