/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SpringIntegrationAndRabbitTest extends AbstractRabbitProducerConsumerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeEach
  public void setupSpec() {
    startRabbit(null);
  }

  @AfterEach
  public void cleanupSpec() {
    stopRabbit();
  }

  @Test
  public void shouldCooperateWithExistingRabbitMQInstrumentation() {
    runWithSpan("parent", () -> producerContext.getBean("producer", Runnable.class).run());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("producer").hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("exchange.declare")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq"))),
                span ->
                    span.hasName("testTopic publish")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("publish")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> {})),
                // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue
                // immediately after receiving
                // that's why the rabbitmq CONSUMER span will never have any child span (and
                // propagate context, actually)
                span ->
                    span.satisfies(
                            spanData -> {
                              assertThat(spanData.getName())
                                  .matches("testTopic.anonymous.[-\\w]+ process/");
                            })
                        .hasParent(trace.getSpan(3))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("process")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> {})),
                // spring-integration will detect that spring-rabbit has already created a consumer
                // span and back off
                span ->
                    span.hasName("testTopic process")
                        .hasParent(trace.getSpan(3))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("process")),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, s -> {}),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> {})),
                span -> span.hasName("consumer").hasParent(trace.getSpan(5))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("basic.ack")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT, l -> {}),
                            OpenTelemetryAssertions.satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            OpenTelemetryAssertions.satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")))));
  }
}
