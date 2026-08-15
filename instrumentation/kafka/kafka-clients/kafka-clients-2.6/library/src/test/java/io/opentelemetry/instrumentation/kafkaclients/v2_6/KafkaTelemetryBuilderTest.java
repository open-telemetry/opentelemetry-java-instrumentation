/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    KafkaTelemetry telemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("*"))
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    KafkaTelemetry telemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(IncludeExclude.builder().setIncluded("*").build())
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isNotEmpty();
  }

  private static List<String> capturedHeaderKeys(KafkaTelemetry telemetry) {
    ProducerRecord<String, String> record = new ProducerRecord<>("topic", "value");
    record.headers().add("Test-Message-Header", "test".getBytes(UTF_8));

    try (MockProducer<String, String> mockProducer =
        new MockProducer<>(true, new StringSerializer(), new StringSerializer())) {
      Producer<String, String> producer = telemetry.wrap(mockProducer);
      producer.send(record);
    }

    List<SpanData> spans = testing.waitForTraces(1).get(0);
    return spans.get(0).getAttributes().asMap().keySet().stream()
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
