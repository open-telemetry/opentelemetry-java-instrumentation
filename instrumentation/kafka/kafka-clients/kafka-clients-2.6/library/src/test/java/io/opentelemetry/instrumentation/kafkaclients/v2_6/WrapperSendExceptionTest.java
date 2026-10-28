/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
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
  @SuppressWarnings("unchecked")
  void producerSpanEndedWhenSendThrowsSynchronously() {
    Producer<String, String> producer = mock(Producer.class);
    when(producer.send(any(), any())).thenThrow(new KafkaException("send failed"));

    KafkaTelemetry telemetry = KafkaTelemetry.builder(testing.getOpenTelemetry()).build();
    Producer<String, String> wrappedProducer = telemetry.instrument(producer);

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
                    span.hasName("send test-topic")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-topic"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "test-key"),
                            equalTo(ERROR_TYPE, KafkaException.class.getName()))));
  }
}
