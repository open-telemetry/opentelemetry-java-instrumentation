/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

class WrapperTest extends AbstractWrapperTest {

  @Override
  void configure(KafkaTelemetryBuilder builder) {
    builder.setMessagingReceiveInstrumentationEnabled(true);
  }

  @Override
  void assertTraces(boolean testHeaders) {
    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes(testHeaders)),
              span ->
                  span.hasName("producer callback")
                      .hasKind(SpanKind.INTERNAL)
                      .hasParent(trace.getSpan(0)));
          SpanContext spanContext = trace.getSpan(1).getSpanContext();
          producerSpanContext.set(
              SpanContext.createFromRemoteParent(
                  spanContext.getTraceId(),
                  spanContext.getSpanId(),
                  spanContext.getTraceFlags(),
                  spanContext.getTraceState()));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(SHARED_TOPIC + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinksSatisfying(links -> assertThat(links).isEmpty())
                        .hasAttributesSatisfyingExactly(receiveAttributes(testHeaders)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpanContext.get()))
                        .hasAttributesSatisfyingExactly(processAttributes(greeting, testHeaders)),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected static List<AttributeAssertion> sendAttributes(boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, "publish"),
                satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("producer")),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative)));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> processAttributes(String greeting, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, "process"),
                equalTo(
                    MESSAGING_MESSAGE_BODY_SIZE, greeting.getBytes(StandardCharsets.UTF_8).length),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                satisfies(
                    AttributeKey.longKey("kafka.record.queue_time_ms"),
                    AbstractLongAssert::isNotNegative),
                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                satisfies(
                    MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer"))));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected static List<AttributeAssertion> receiveAttributes(boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                equalTo(MESSAGING_OPERATION, "receive"),
                equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }
}
