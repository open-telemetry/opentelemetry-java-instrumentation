/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientPropagationBaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaClientPropagationDisabledTest extends KafkaClientPropagationBaseTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @DisplayName("should not read remote context when consuming messages if propagation is disabled")
  @Test
  void testReadRemoteContextWhenPropagationIsDisabled() throws InterruptedException {
    String message = "Testing without headers";
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message));

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertSendSpan(span, message)));

    awaitUntilConsumerIsReady();

    ConsumerRecords<?, ?> records = poll(Duration.ofSeconds(5));
    assertThat(records.count()).isEqualTo(1);

    // iterate over records to generate spans
    for (ConsumerRecord<?, ?> ignored : records) {
      testing.runWithSpan("processing", () -> {});
    }

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> assertSendSpan(span, message)),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("poll " + SHARED_TOPIC)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(emptyList())
                          .hasAttributesSatisfyingExactly(receiveAttributes(false))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("process " + SHARED_TOPIC)
                          .hasKind(SpanKind.CONSUMER)
                          .hasNoParent()
                          .hasLinks(emptyList())
                          .hasAttributesSatisfyingExactly(
                              processAttributes(null, message, false, false)),
                  span -> span.hasName("processing").hasParent(trace.getSpan(0))));
      return;
    }

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertSendSpan(span, message)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(emptyList())
                        .hasAttributesSatisfyingExactly(
                            processAttributes(null, message, false, false)),
                span -> span.hasName("processing").hasParent(trace.getSpan(0))));
  }

  // when propagation is disabled the span context is not sent as the message creation context, so
  // under the stable semantic conventions the send span is CLIENT instead of PRODUCER
  private static void assertSendSpan(SpanDataAssert span, String message) {
    span.hasName(emitStableMessagingSemconv() ? "send " + SHARED_TOPIC : SHARED_TOPIC + " publish")
        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(sendAttributes(null, message, false));
  }
}
