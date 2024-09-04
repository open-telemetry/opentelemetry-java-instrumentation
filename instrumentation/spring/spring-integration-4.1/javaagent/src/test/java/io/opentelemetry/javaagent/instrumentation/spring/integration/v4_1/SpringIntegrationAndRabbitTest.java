/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringIntegrationAndRabbitTest {

  @RegisterExtension RabbitExtension rabbit;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  SpringIntegrationAndRabbitTest() {
    rabbit = new RabbitExtension(null);
  }

  @Test
  void shouldCooperateWithExistingRabbitMqInstrumentation() {
    testing.waitForTraces(13); // from rabbitmq instrumentation of startup
    testing.clearData();

    runWithSpan("parent", () -> rabbit.getBean("producer", Runnable.class).run());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName("producer").hasParent(trace.getSpan(0)).hasTotalAttributeCount(0),
                span -> span.hasName("exchange.declare"),
                span ->
                    span.hasName("exchange.declare")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq")),
                span -> span.hasName("queue.declare"),
                span -> span.hasName("queue.bind"),
                span ->
                    span.hasName("testTopic publish")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "testTopic"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> s.isInstanceOf(String.class))),
                // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue
                // immediately after receiving
                // that's why the rabbitmq CONSUMER span will never have any child span (and
                // propagate context, actually)
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("testTopic.anonymous.[-\\w]+ process"))
                        .hasParent(trace.getSpan(6))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "testTopic"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> s.isInstanceOf(String.class))),
                // spring-integration will detect that spring-rabbit has already created a consumer
                // span and back off
                span ->
                    span.hasName("testTopic process")
                        .hasParent(trace.getSpan(6))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                "testTopic"),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                s -> s.isInstanceOf(String.class)),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class))),
                span ->
                    span.hasName("consumer").hasParent(trace.getSpan(8)).hasTotalAttributeCount(0)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("basic.ack")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"))));
  }
}
