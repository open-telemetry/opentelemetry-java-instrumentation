/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.RABBITMQ;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

@DisabledIfSystemProperty(
    named = "testLatestDeps",
    matches = "true",
    disabledReason =
        "reactor-rabbitmq 1.5.6 (and earlier) still calls `void useNio()` which was removed in 5.27.0")
class ReactorRabbitMqTest extends AbstractRabbitMqTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testShouldNotFailDeclaringExchange() {
    Sender sender =
        RabbitFlux.createSender(new SenderOptions().connectionFactory(connectionFactory));

    sender.declareExchange(ExchangeSpecification.exchange("testExchange")).block();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("exchange.declare")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, RABBITMQ),
                            equalTo(stringKey("rabbitmq.command"), "exchange.declare"),
                            satisfies(
                                NETWORK_PEER_ADDRESS,
                                addr -> addr.satisfies(a -> assertThat(a).isIn(rabbitMqIp, null))),
                            satisfies(
                                NETWORK_TYPE,
                                type ->
                                    type.satisfies(t -> assertThat(t).isIn("ipv4", "ipv6", null))),
                            satisfies(NETWORK_PEER_PORT, port -> assertThat(port).isNotNull()))));
  }
}
