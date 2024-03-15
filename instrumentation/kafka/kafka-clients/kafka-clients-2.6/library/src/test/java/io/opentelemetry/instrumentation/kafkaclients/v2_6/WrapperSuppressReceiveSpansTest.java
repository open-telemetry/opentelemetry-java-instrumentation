/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;

@SuppressWarnings("deprecation") // old semconv
class WrapperSuppressReceiveSpansTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setMessagingReceiveInstrumentationEnabled(false);
  }

  @Override
  void assertTraces(boolean testHeaders) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(testHeaders)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(processAttributes(greeting, testHeaders)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  protected static List<AttributeAssertion> sendAttributes(boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                satisfies(
                    SemanticAttributes.MESSAGING_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("producer")),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  private static List<AttributeAssertion> processAttributes(String greeting, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                equalTo(
                    SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                    greeting.getBytes(StandardCharsets.UTF_8).length),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    AttributeKey.longKey("kafka.record.queue_time_ms"),
                    AbstractLongAssert::isNotNegative),
                equalTo(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                satisfies(
                    SemanticAttributes.MESSAGING_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("consumer"))));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }
}
