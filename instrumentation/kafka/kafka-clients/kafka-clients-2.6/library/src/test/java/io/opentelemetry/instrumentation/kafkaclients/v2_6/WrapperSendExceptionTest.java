/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class WrapperSendExceptionTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  @SuppressWarnings({"unchecked"})
  void producerSpanEndedWhenSendThrowsSynchronously() {
    Producer<String, String> producer = mock(Producer.class);
    when(producer.send(any(), any())).thenThrow(new KafkaException("send failed"));

    KafkaTelemetry telemetry = KafkaTelemetry.builder(testing.getOpenTelemetry()).build();
    Producer<String, String> wrappedProducer = telemetry.wrap(producer);

    ProducerRecord<String, String> record =
        new ProducerRecord<>("test-topic", "test-key", "test-value");

    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> wrappedProducer.send(record)))
        .isInstanceOf(KafkaException.class)
        .hasMessage("send failed");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("test-topic publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())));
  }
}
