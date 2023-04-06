/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ShutdownSignalException
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import reactor.rabbitmq.ExchangeSpecification
import reactor.rabbitmq.RabbitFlux
import reactor.rabbitmq.SenderOptions

class ReactorRabbitMqTest extends AgentInstrumentationSpecification implements WithRabbitMqTrait {

  // Open connection outside of the test method to ensure that connection can be successfully
  // established inside the test method without producing extra spans for connection recovery
  Connection conn = connectionFactory.newConnection()
  Channel channel = conn.createChannel()

  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def cleanup() {
    try {
      channel.close()
      conn.close()
    } catch (ShutdownSignalException ignored) {
    }
  }

  def "should not fail declaring exchange"() {
    given:
    def sender = RabbitFlux.createSender(new SenderOptions().connectionFactory(connectionFactory))

    when:
    sender.declareExchange(ExchangeSpecification.exchange("testExchange"))
      .block()

    then:
    noExceptionThrown()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name 'exchange.declare'
          kind SpanKind.CLIENT
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "$SemanticAttributes.NET_SOCK_PEER_PORT" Long
            "$SemanticAttributes.NET_SOCK_FAMILY" { it == SemanticAttributes.NetSockFamilyValues.INET6 || it == null }
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "rabbitmq.command" "exchange.declare"
          }
        }
      }
    }

    cleanup:
    sender?.close()
  }
}
