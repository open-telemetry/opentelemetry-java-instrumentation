/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.producer.ProducerRecord

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class KafkaClientPropagationDisabledTest extends KafkaClientPropagationBaseTest {

  def "should not read remote context when consuming messages if propagation is disabled"() {
    when: "send message"
    String message = "Testing without headers"
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message))

    then: "producer span is created"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }
      }
    }

    when: "read message without context propagation"
    awaitUntilConsumerIsReady()
    def records = consumer.poll(Duration.ofSeconds(5).toMillis())
    records.count() == 1

    // iterate over records to generate spans
    for (record in records) {
      runWithSpan("processing") {}
    }

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
          }
        }
      }
      trace(1, 2) {
        span(0) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          hasNoLinks()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "kafka.offset" Long
            "kafka.record.queue_time_ms" { it >= 0 }
          }
          span(1) {
            name "processing"
            childOf span(0)
          }
        }
      }
    }
  }
}