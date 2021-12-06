/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients

import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.producer.ProducerRecord

import java.time.Duration
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class KafkaClientDefaultTest extends KafkaClientPropagationBaseTest {

  def "test kafka produce and consume"() {
    when:
    String greeting = "Hello Kafka!"
    runWithSpan("parent") {
      producer.send(new ProducerRecord(SHARED_TOPIC, greeting)) { meta, ex ->
        if (ex == null) {
          runWithSpan("producer callback") {}
        } else {
          runWithSpan("producer exception: " + ex) {}
        }
      }.get(5, TimeUnit.SECONDS)
    }

    then:
    awaitUntilConsumerIsReady()
    def records = consumer.poll(Duration.ofSeconds(5).toMillis())
    records.count() == 1

    // iterate over records to generate spans
    for (record in records) {
      runWithSpan("processing") {
        assert record.value() == greeting
        assert record.key() == null
      }
    }

    assertTraces(2) {
      traces.sort(orderByRootSpanKind(INTERNAL, CONSUMER))

      SpanData producerSpan

      trace(0, 3) {
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
          name "producer callback"
          kind INTERNAL
          childOf span(0)
        }

        producerSpan = span(1)
      }
      trace(1, 3) {
        span(0) {
          name SHARED_TOPIC + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          childOf span(0)
          hasLink producerSpan
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
        }
        span(2) {
          name "processing"
          childOf span(1)
        }
      }
    }
  }

  def "test pass through tombstone"() {
    when:
    producer.send(new ProducerRecord<>(SHARED_TOPIC, null)).get(5, TimeUnit.SECONDS)

    then:
    awaitUntilConsumerIsReady()
    def records = consumer.poll(Duration.ofSeconds(5).toMillis())
    records.count() == 1

    // iterate over records to generate spans
    for (record in records) {
      assert record.value() == null
      assert record.key() == null
    }

    assertTraces(2) {
      traces.sort(orderByRootSpanKind(PRODUCER, CONSUMER))

      SpanData producerSpan

      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE" true
          }
        }

        producerSpan = span(0)
      }
      trace(1, 2) {
        span(0) {
          name SHARED_TOPIC + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          childOf span(0)
          hasLink producerSpan
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
            "$SemanticAttributes.MESSAGING_KAFKA_TOMBSTONE" true
            "kafka.offset" Long
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
      }
    }
  }

  def "test records(TopicPartition) kafka consume"() {
    setup:
    def partition = 0

    when: "send message"
    def greeting = "Hello from MockConsumer!"
    producer.send(new ProducerRecord<>(SHARED_TOPIC, partition, null, greeting)).get(5, TimeUnit.SECONDS)

    then: "wait for PRODUCER span"
    waitForTraces(1)

    when: "receive messages"
    awaitUntilConsumerIsReady()
    def consumerRecords = consumer.poll(Duration.ofSeconds(5).toMillis())
    def recordsInPartition = consumerRecords.records(topicPartition)
    recordsInPartition.size() == 1

    // iterate over records to generate spans
    for (record in recordsInPartition) {
      assert record.value() == greeting
      assert record.key() == null
    }

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanKind(PRODUCER, CONSUMER))

      SpanData producerSpan

      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_KAFKA_PARTITION" { it >= 0 }
          }
        }

        producerSpan = span(0)
      }
      trace(1, 2) {
        span(0) {
          name SHARED_TOPIC + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "kafka"
            "$SemanticAttributes.MESSAGING_DESTINATION" SHARED_TOPIC
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          childOf span(0)
          hasLink producerSpan
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
        }
      }
    }
  }
}