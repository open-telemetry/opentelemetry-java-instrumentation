/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.nio.charset.StandardCharsets
import org.apache.kafka.clients.producer.ProducerRecord
import spock.lang.Unroll

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static java.util.Collections.singletonList

class WrappersTest extends KafkaClientBaseTest implements LibraryTestTrait {

  @Unroll
  def "test wrappers, test headers: #testHeaders"() throws Exception {
    KafkaTelemetry telemetry = KafkaTelemetry.builder(getOpenTelemetry())
      .setCapturedHeaders(singletonList("test-message-header"))
      // TODO run tests both with and without experimental span attributes
      .setCaptureExperimentalSpanAttributes(true)
      .build()

    when:
    String greeting = "Hello Kafka!"
    def wrappedProducer = telemetry.wrap(producer)
    runWithSpan("parent") {
      def producerRecord = new ProducerRecord(SHARED_TOPIC, greeting)
      if (testHeaders) {
        producerRecord.headers().add("test-message-header", "test".getBytes(StandardCharsets.UTF_8))
      }
      wrappedProducer.send(producerRecord) { meta, ex ->
        if (ex == null) {
          runWithSpan("producer callback") {}
        } else {
          runWithSpan("producer exception: " + ex) {}
        }
      }
    }

    then:
    awaitUntilConsumerIsReady()
    // check that the message was received
    def wrappedConsumer = telemetry.wrap(consumer)
    def records = wrappedConsumer.poll(Duration.ofSeconds(5).toMillis())
    records.count() == 1
    for (record in records) {
      assert record.value() == greeting
      assert record.key() == null
    }

    assertTraces(1) {
      traces.sort(orderByRootSpanKind(INTERNAL, PRODUCER, CONSUMER))
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "messaging.kafka.message.offset" { it >= 0 }
            if (testHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(2) {
          name SHARED_TOPIC + " receive"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "messaging.kafka.message.offset" { it >= 0 }
            "kafka.record.queue_time_ms" { it >= 0 }
            if (testHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(3) {
          name "producer callback"
          kind INTERNAL
          childOf span(0)
        }
      }
    }

    where:
    testHeaders << [false, true]
  }
}
