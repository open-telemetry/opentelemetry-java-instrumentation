/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static com.google.common.net.InetAddresses.isInetAddress
import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.server.ServerTraceUtils.runUnderServerTrace

class SpringIntegrationAndRabbitTest extends AgentInstrumentationSpecification implements WithRabbitProducerConsumerTrait {
  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def "should cooperate with existing RabbitMQ instrumentation"() {
    when:
    // simulate the workflow being triggered by HTTP request
    runUnderServerTrace("HTTP GET") {
      producerContext.getBean("producer", Runnable).run()
    }

    then:
    assertTraces(2) {
      trace(0, 7) {
        span(0) {
          name "HTTP GET"
          kind SERVER
          attributes {}
        }
        span(1) {
          name "producer"
          childOf span(0)
          attributes {}
        }
        span(2) {
          // span created by rabbitmq instrumentation
          name "exchange.declare"
          childOf span(1)
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" { it == null || it == "localhost" }
            "$SemanticAttributes.NET_PEER_IP" { isInetAddress(it as String) }
            "$SemanticAttributes.NET_PEER_PORT" { it == null || it instanceof Long }
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
          }
        }
        span(3) {
          // span created by rabbitmq instrumentation
          name "testTopic send"
          childOf span(1)
          kind PRODUCER
          attributes {
            // "localhost" on linux, null on windows
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_PEER_IP" { isInetAddress(it as String) }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" String
          }
        }
        // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue immediately after receiving
        // that's why the rabbitmq CONSUMER span will never have any child span (and propagate context, actually)
        span(4) {
          // span created by rabbitmq instrumentation
          name ~/testTopic.anonymous.[-\w]+ process/
          childOf span(3)
          kind CONSUMER
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" String
          }
        }
        // spring-integration will detect that spring-rabbit has already created a consumer span and back off
        span(5) {
          // span created by spring-rabbit instrumentation
          name "testTopic process"
          childOf span(3)
          kind CONSUMER
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION" "testTopic"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
          }
        }
        span(6) {
          name "consumer"
          childOf span(5)
          attributes {}
        }
      }

      trace(1, 1) {
        span(0) {
          // span created by rabbitmq instrumentation
          name "basic.ack"
          kind CLIENT
          attributes {
            // "localhost" on linux, null on windows
            "$SemanticAttributes.NET_PEER_NAME" { it == "localhost" || it == null }
            "$SemanticAttributes.NET_PEER_IP" { isInetAddress(it as String) }
            "$SemanticAttributes.NET_PEER_PORT" Long
            "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"
          }
        }
      }
    }
  }
}
