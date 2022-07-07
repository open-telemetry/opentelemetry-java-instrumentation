/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import reactor.rabbitmq.ExchangeSpecification
import reactor.rabbitmq.RabbitFlux
import reactor.rabbitmq.SenderOptions

class ReactorRabbitMqTest extends AgentInstrumentationSpecification implements WithRabbitMqTrait {

  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
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
            // "localhost" on linux, "127.0.0.1" on windows
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "net.sock.peer.addr" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "net.sock.family" { it == null || it == "inet6" }
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
