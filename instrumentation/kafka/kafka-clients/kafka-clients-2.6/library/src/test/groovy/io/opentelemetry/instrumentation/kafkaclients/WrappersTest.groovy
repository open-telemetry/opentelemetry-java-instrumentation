/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.producer.ProducerRecord
import spock.lang.Unroll

import java.time.Duration

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class WrappersTest extends KafkaClientBaseTest implements LibraryTestTrait {

  @Unroll
  def "test wrappers"() throws Exception {
    KafkaTelemetry telemetry = KafkaTelemetry.create(getOpenTelemetry())

    when:
    String greeting = "Hello Kafka!"
    def wrappedProducer = telemetry.wrap(producer)
    runWithSpan("parent") {
      wrappedProducer.send(new ProducerRecord(SHARED_TOPIC, greeting)) { meta, ex ->
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
            "kafka.offset" Long
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
        span(3) {
          name "producer callback"
          kind INTERNAL
          childOf span(0)
        }
      }
    }
  }
}

