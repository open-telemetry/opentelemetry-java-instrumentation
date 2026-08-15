/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.kafka.listener.RecordInterceptor;

class SpringKafkaTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    SpringKafkaTelemetry telemetry =
        SpringKafkaTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("*"))
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    SpringKafkaTelemetry telemetry =
        SpringKafkaTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(IncludeExclude.builder().setIncluded("*").build())
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isNotEmpty();
  }

  private static List<String> capturedHeaderKeys(SpringKafkaTelemetry telemetry) {
    ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "value");
    record.headers().add("Test-Message-Header", "test".getBytes(UTF_8));

    try (Consumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST)) {
      RecordInterceptor<String, String> interceptor = telemetry.createRecordInterceptor();
      interceptor.intercept(record, consumer);
      interceptor.success(record, consumer);
    }

    List<SpanData> spans = testing.waitForTraces(1).get(0);
    return spans.get(0).getAttributes().asMap().keySet().stream()
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
