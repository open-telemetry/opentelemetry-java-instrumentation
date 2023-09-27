/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.Sender;
import reactor.rabbitmq.SenderOptions;

class ReactorRabbitMqTest extends AbstractRabbitMqTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // Ignoring deprecation warning for use of SemanticAttributes
  @SuppressWarnings("deprecation")
  @Test
  void testShouldNotFailDeclaringExchange() {
    Sender sender =
        RabbitFlux.createSender(new SenderOptions().connectionFactory(connectionFactory));

    try {
      sender.declareExchange(ExchangeSpecification.exchange("testExchange")).block();
    } catch (RuntimeException e) {
      Assertions.fail("Should not fail declaring exchange", e);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace
                .hasSize(1)
                .hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName("exchange.declare")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttribute(SemanticAttributes.MESSAGING_SYSTEM, "rabbitmq")
                          .hasAttribute(
                              AttributeKey.stringKey("rabbitmq.command"), "exchange.declare")
                          .hasAttributesSatisfying(
                              attributes ->
                                  assertThat(attributes)
                                      .satisfies(
                                          attrs -> {
                                            String peerAddr =
                                                attrs.get(SemanticAttributes.NET_SOCK_PEER_ADDR);
                                            assertTrue(
                                                "127.0.0.1".equals(peerAddr)
                                                    || "0:0:0:0:0:0:0:1".equals(peerAddr)
                                                    || peerAddr == null);

                                            String sockFamily =
                                                attrs.get(SemanticAttributes.NET_SOCK_FAMILY);
                                            assertTrue(
                                                SemanticAttributes.NetSockFamilyValues.INET6.equals(
                                                        sockFamily)
                                                    || sockFamily == null);
                                            assertNotNull(
                                                attrs.get(SemanticAttributes.NET_SOCK_PEER_PORT));
                                          }));
                    }));
  }
}
