/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringIntegrationAndRabbitTest {

  @RegisterExtension private final RabbitExtension rabbit;

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  SpringIntegrationAndRabbitTest() {
    rabbit = new RabbitExtension(null);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
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
                                NETWORK_PEER_ADDRESS,
                                val -> val.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                            satisfies(NETWORK_TYPE, val -> val.isIn("ipv4", "ipv6", null)),
                            equalTo(MESSAGING_SYSTEM, "rabbitmq")),
                span -> span.hasName("queue.declare"),
                span -> span.hasName("queue.bind"),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "publish testTopic:testTopic"
                                : "testTopic publish")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NETWORK_PEER_ADDRESS,
                                val -> val.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                            satisfies(NETWORK_TYPE, val -> val.isIn("ipv4", "ipv6", null)),
                            serverAddress(),
                            serverPort(),
                            equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                            equalTo(
                                MESSAGING_DESTINATION_NAME,
                                emitStableMessagingSemconv() ? "testTopic:testTopic" : "testTopic"),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            bodySize(),
                            satisfies(
                                MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                val -> val.isInstanceOf(String.class))),
                // the rabbitmq CONSUMER span is suppressed for Spring listener containers (see
                // RabbitMqConsumerProcessTracing), so spring-rabbit creates the single process span
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches(
                                        emitStableMessagingSemconv()
                                            ? "process"
                                            : "testTopic process"))
                        .hasParent(trace.getSpan(6))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            serverAddress(),
                            serverPort(),
                            equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                            consumerDestinationName(),
                            anonymousDestination(),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "process" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "process" : null),
                            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                            bodySize(),
                            equalTo(
                                MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                emitStableMessagingSemconv() ? "testTopic" : null),
                            deliveryTag()),
                span ->
                    span.hasName("consumer").hasParent(trace.getSpan(7)).hasTotalAttributeCount(0)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "ack" : "basic.ack")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(ackAssertions())));

    // Rabbit and Spring Rabbit own the active messaging layers, so Spring Integration must not
    // start operation listeners that could duplicate their metric points.
    assertNoMetrics(testing);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> ackAssertions() {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                satisfies(
                    NETWORK_PEER_ADDRESS, val -> val.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                satisfies(NETWORK_TYPE, val -> val.isIn("ipv4", "ipv6", null)),
                equalTo(MESSAGING_SYSTEM, "rabbitmq")));
    if (emitStableMessagingSemconv()) {
      assertions.add(serverAddress());
      assertions.add(serverPort());
      assertions.add(equalTo(MESSAGING_OPERATION_NAME, "ack"));
      assertions.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
      assertions.add(deliveryTag());
      if (emitOldMessagingSemconv()) {
        assertions.add(equalTo(MESSAGING_OPERATION, "settle"));
      }
    }
    return assertions;
  }

  private static AttributeAssertion serverAddress() {
    return satisfies(
        SERVER_ADDRESS,
        val -> {
          if (emitStableMessagingSemconv()) {
            val.isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
          } else {
            val.isNull();
          }
        });
  }

  private static AttributeAssertion serverPort() {
    return satisfies(
        SERVER_PORT,
        val -> {
          if (emitStableMessagingSemconv()) {
            val.isInstanceOf(Long.class);
          } else {
            val.isNull();
          }
        });
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static AttributeAssertion bodySize() {
    return satisfies(
        MESSAGING_MESSAGE_BODY_SIZE,
        val -> {
          if (emitOldMessagingSemconv()) {
            val.isInstanceOf(Long.class);
          } else {
            val.isNull();
          }
        });
  }

  private static AttributeAssertion deliveryTag() {
    return satisfies(
        MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG,
        val -> {
          if (emitStableMessagingSemconv()) {
            val.isNotNegative();
          } else {
            val.isNull();
          }
        });
  }

  private static AttributeAssertion consumerDestinationName() {
    return satisfies(
        MESSAGING_DESTINATION_NAME,
        val -> {
          if (emitStableMessagingSemconv()) {
            val.matches("testTopic:testTopic:testTopic\\.anonymous\\.[A-Za-z0-9_-]{22}");
          } else {
            val.isEqualTo("testTopic");
          }
        });
  }

  private static AttributeAssertion anonymousDestination() {
    return equalTo(MESSAGING_DESTINATION_ANONYMOUS, emitStableMessagingSemconv() ? true : null);
  }
}
