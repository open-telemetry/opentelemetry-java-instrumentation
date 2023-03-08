/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.MessageListener
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class PulsarClientTest extends AgentInstrumentationSpecification {
  private static final Logger logger = LoggerFactory.getLogger(PulsarClientTest)

  private static final DockerImageName DEFAULT_IMAGE_NAME =
    DockerImageName.parse("apachepulsar/pulsar:2.8.0")

  @Shared
  private PulsarContainer pulsar
  @Shared
  private PulsarClient client
  @Shared
  private PulsarAdmin admin
  @Shared
  private Producer<String> producer
  @Shared
  private Consumer<String> consumer
  @Shared
  private Producer<String> producer1

  @Shared
  private String brokerUrl

  @Override
  def setupSpec() {
    pulsar = new PulsarContainer(DEFAULT_IMAGE_NAME)
      .withEnv("PULSAR_MEM", "-Xmx128m")
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withStartupTimeout(Duration.ofMinutes(2))
    pulsar.start()

    brokerUrl = pulsar.pulsarBrokerUrl
    client = PulsarClient.builder().serviceUrl(brokerUrl).build()
    admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.httpServiceUrl).build()
  }

  @Override
  def cleanupSpec() {
    producer?.close()
    consumer?.close()
    producer1?.close()
    client?.close()
    admin?.close()
    pulsar.close()
  }

  def "test send non-partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testSendNonPartitionedTopic"
    admin.topics().createNonPartitionedTopic(topic)
    producer =
      client.newProducer(Schema.STRING).topic(topic)
        .enableBatching(false).create()

    when:
    String msg = "test"
    def msgId = runWithSpan("parent") {
      producer.send(msg)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "PRODUCER/SEND"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
          }
        }
      }
    }
  }

  def "test consume non-partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopic"
    def latch = new CountDownLatch(1)
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
          latch.countDown()
        }
      })
      .subscribe()

    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()

    when:
    def msg = "test"
    def msgId = runWithSpan("parent") {
      producer.send(msg)
    }

    latch.await(1, TimeUnit.MINUTES)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "PRODUCER/SEND"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
          }
        }
        span(2) {
          name "CONSUMER/RECEIVE"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(3) {
          name "CONSUMER/PROCESS"
          kind INTERNAL
          childOf span(2)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
          }
        }
      }
    }
  }

  def "test send partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testSendPartitionedTopic"
    admin.topics().createPartitionedTopic(topic, 2)
    producer =
      client.newProducer(Schema.STRING).topic(topic)
        .enableBatching(false).create()

    when:
    String msg = "test"
    def msgId = runWithSpan("parent") {
      producer.send(msg)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "PRODUCER/SEND"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" {
              t ->
                return t.toString().contains(topic)
            }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
          }
        }
      }
    }
  }

  def "test consume partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testConsumePartitionedTopic"
    admin.topics().createPartitionedTopic(topic, 2)

    def latch = new CountDownLatch(1)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .topic(topic)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
          latch.countDown()
        }
      })
      .subscribe()

    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()

    when:
    def msg = "test"
    def msgId = runWithSpan("parent") {
      producer.send(msg)
    }

    latch.await(1, TimeUnit.MINUTES)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "PRODUCER/SEND"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
          }
        }
        span(2) {
          name "CONSUMER/RECEIVE"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(3) {
          name "CONSUMER/PROCESS"
          kind INTERNAL
          childOf span(2)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
      }
    }
  }

  def "test consume multi-topics"() {
    setup:

    def topicNamePrefix = "persistent://public/default/testConsumeMulti_"
    def topic = topicNamePrefix + "1"
    def topic1 = topicNamePrefix + "2"

    def latch = new CountDownLatch(2)
    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()
    producer1 = client.newProducer(Schema.STRING)
      .topic(topic1)
      .enableBatching(false)
      .create()

    when:
    runWithSpan("parent1") {
      producer.send("test1")
    }
    runWithSpan("parent2") {
      producer1.send("test2")
    }

    consumer = client.newConsumer(Schema.STRING)
      .topic(topic1, topic)
      .subscriptionName("test_sub")
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
          latch.countDown()
        }
      })
      .subscribe()

    latch.await(1, TimeUnit.MINUTES)

    then:
    assertTraces(2) {
      traces.sort(orderByRootSpanName("parent1", "parent2"))
      for (int i in 1..2) {
        trace(i - 1, 4) {
          span(0) {
            name "parent" + i
            kind INTERNAL
            hasNoParent()
          }
          span(1) {
            name "PRODUCER/SEND"
            kind PRODUCER
            childOf span(0)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
            }
          }
          span(1) {
            name "PRODUCER/SEND"
            kind PRODUCER
            childOf span(0)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
            }
          }
          span(2) {
            name "CONSUMER/RECEIVE"
            kind CONSUMER
            childOf span(1)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.NET_SOCK_PEER_ADDR" brokerUrl
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            }
          }
          span(3) {
            name "CONSUMER/PROCESS"
            kind INTERNAL
            childOf span(2)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "$SemanticAttributes.MESSAGING_OPERATION" "process"
            }
          }
        }
      }
    }
  }
}
