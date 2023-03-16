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
  private Producer<String> producer2

  @Shared
  private String brokerHost
  @Shared
  private int brokerPort

  @Override
  def setupSpec() {
    pulsar = new PulsarContainer(DEFAULT_IMAGE_NAME)
      .withEnv("PULSAR_MEM", "-Xmx128m")
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withStartupTimeout(Duration.ofMinutes(2))
    pulsar.start()

    brokerHost = pulsar.host
    brokerPort = pulsar.getMappedPort(6650)
    client = PulsarClient.builder().serviceUrl(pulsar.pulsarBrokerUrl).build()
    admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.httpServiceUrl).build()
  }

  @Override
  def cleanupSpec() {
    producer?.close()
    consumer?.close()
    producer2?.close()
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
          name "$topic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.pulsar.message.type" "normal"
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
          name "$topic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.pulsar.message.type" "normal"
          }
        }
        span(2) {
          name "$topic receive"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(3) {
          name "$topic process"
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

  def "capture message header as span attribute"() {
    setup:
    def topic = "persistent://public/default/testCaptureMessageHeaderTopic"
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
      producer.newMessage().value(msg).property("test-message-header", "test").send()
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
          name "$topic send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.pulsar.message.type" "normal"
            "messaging.header.test_message_header" { it == ["test"] }
          }
        }
        span(2) {
          name "$topic receive"
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "messaging.header.test_message_header" { it == ["test"] }
          }
        }
        span(3) {
          name "$topic process"
          kind INTERNAL
          childOf span(2)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" topic
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.header.test_message_header" { it == ["test"] }
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
          name ~/${topic}-partition-.*send/
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.pulsar.message.type" "normal"
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
          name ~/${topic}-partition-.*send/
          kind PRODUCER
          childOf span(0)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "messaging.pulsar.message.type" "normal"
          }
        }
        span(2) {
          name ~/${topic}-partition-.*receive/
          kind CONSUMER
          childOf span(1)
          attributes {
            "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
            "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
            "$SemanticAttributes.NET_PEER_NAME" brokerHost
            "$SemanticAttributes.NET_PEER_PORT" brokerPort
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topic) }
            "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
          }
        }
        span(3) {
          name ~/${topic}-partition-.*process/
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
    def topic1 = topicNamePrefix + "1"
    def topic2 = topicNamePrefix + "2"

    def latch = new CountDownLatch(2)
    producer = client.newProducer(Schema.STRING)
      .topic(topic1)
      .enableBatching(false)
      .create()
    producer2 = client.newProducer(Schema.STRING)
      .topic(topic2)
      .enableBatching(false)
      .create()

    when:
    runWithSpan("parent1") {
      producer.send("test1")
    }
    runWithSpan("parent2") {
      producer2.send("test2")
    }

    consumer = client.newConsumer(Schema.STRING)
      .topic(topic2, topic1)
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
        def topic = i == 1 ? topic1 : topic2
        trace(i - 1, 4) {
          span(0) {
            name "parent" + i
            kind INTERNAL
            hasNoParent()
          }
          span(1) {
            name "$topic send"
            kind PRODUCER
            childOf span(0)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.NET_PEER_NAME" brokerHost
              "$SemanticAttributes.NET_PEER_PORT" brokerPort
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "messaging.pulsar.message.type" "normal"
            }
          }
          span(2) {
            name "$topic receive"
            kind CONSUMER
            childOf span(1)
            attributes {
              "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
              "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
              "$SemanticAttributes.NET_PEER_NAME" brokerHost
              "$SemanticAttributes.NET_PEER_PORT" brokerPort
              "$SemanticAttributes.MESSAGING_DESTINATION_NAME" { it.startsWith(topicNamePrefix) }
              "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
              "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
              "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            }
          }
          span(3) {
            name  "$topic process"
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
