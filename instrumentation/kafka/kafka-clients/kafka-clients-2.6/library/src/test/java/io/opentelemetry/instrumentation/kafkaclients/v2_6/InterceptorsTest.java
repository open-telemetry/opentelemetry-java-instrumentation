/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
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

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

class InterceptorsTest extends AbstractInterceptorsTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  void assertTraces() {
    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", SHARED_TOPIC + " receive", "producer callback"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(SHARED_TOPIC + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, "kafka"),
                          equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                          equalTo(MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MESSAGING_CLIENT_ID,
                              stringAssert -> stringAssert.startsWith("producer"))));
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer")),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)),
                span ->
                    span.hasName(SHARED_TOPIC + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpanContext.get()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "kafka"),
                            equalTo(MESSAGING_DESTINATION_NAME, SHARED_TOPIC),
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(
                                MESSAGING_MESSAGE_BODY_SIZE,
                                greeting.getBytes(StandardCharsets.UTF_8).length),
                            satisfies(
                                MESSAGING_DESTINATION_PARTITION_ID,
                                AbstractStringAssert::isNotEmpty),
                            satisfies(
                                MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative),
                            equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"),
                            satisfies(
                                MESSAGING_CLIENT_ID,
                                stringAssert -> stringAssert.startsWith("consumer"))),
                span ->
                    span.hasName("process child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))),
        // ideally we'd want producer callback to be part of the main trace, we just aren't able to
        // instrument that
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("producer callback").hasKind(SpanKind.INTERNAL).hasNoParent()));
  }
}
