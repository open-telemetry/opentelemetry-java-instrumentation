/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.opentelemetry.semconv.NetworkAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class SpringIntegrationAndRabbitTest extends AgentInstrumentationSpecification implements WithRabbitProducerConsumerTrait {
  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def "should cooperate with existing RabbitMQ instrumentation"() {
    when:
    runWithSpan("parent") {
      producerContext.getBean("producer", Runnable).run()
    }

    then:
    assertTraces(2) {
      trace(0, 7) {
        span(0) {
          name "parent"
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
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$NetworkAttributes.NETWORK_TYPE" { it == "ipv4" || it == "ipv6" || it == null }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "rabbitmq"
          }
        }
        span(3) {
          // span created by rabbitmq instrumentation
          name "testTopic publish"
          childOf span(1)
          kind PRODUCER
          attributes {
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$NetworkAttributes.NETWORK_TYPE" { it == "ipv4" || it == "ipv6" || it == null }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testTopic"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "publish"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE" Long
            "$MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY" String
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
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$NetworkAttributes.NETWORK_TYPE" { it == "ipv4" || it == "ipv6" || it == null }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testTopic"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE" Long
            "$MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY" String
          }
        }
        // spring-integration will detect that spring-rabbit has already created a consumer span and back off
        span(5) {
          // span created by spring-rabbit instrumentation
          name "testTopic process"
          childOf span(3)
          kind CONSUMER
          attributes {
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "rabbitmq"
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "testTopic"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE" Long
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
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$NetworkAttributes.NETWORK_TYPE" { it == "ipv4" || it == "ipv6" || it == null }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" "rabbitmq"
          }
        }
      }
    }
  }
}
