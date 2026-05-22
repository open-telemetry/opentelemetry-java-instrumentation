/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

@SuppressWarnings("deprecation") // using deprecated semconv
class WrapperSuppressReceiveSpansTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setMessagingReceiveTelemetryEnabled(false);
  }

  @Override
  void assertTraces(boolean testHeaders, boolean testExperimental) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(SHARED_TOPIC + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(testHeaders, testExperimental)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(greeting, testHeaders, testExperimental)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("producer callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  static List<AttributeAssertion> sendAttributes(boolean testHeaders, boolean testExperimental) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, "publish"),
                satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("producer")),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative)));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    if (testExperimental) {
      assertions.add(
          satisfies(
              stringKey("messaging.kafka.bootstrap.servers"),
              val -> val.matches("^localhost:\\d+(,localhost:\\d+)*$")));
    }
    return assertions;
  }

  static List<AttributeAssertion> processAttributes(
      String greeting, boolean testHeaders, boolean testExperimental) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, "process"),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, greeting.getBytes(UTF_8).length),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith("consumer"))));
    if (testHeaders) {
      assertions.add(equalTo(headerAttributeKey("Test-Message-Header"), singletonList("test")));
    }
    if (testExperimental) {
      assertions.add(
          satisfies(longKey("kafka.record.queue_time_ms"), AbstractLongAssert::isNotNegative));
    }
    return assertions;
  }
}
