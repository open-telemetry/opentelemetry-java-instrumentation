/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.semconv.SemanticAttributes
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.MessageListener
import org.apache.pulsar.client.api.Messages
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

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
        producerSpan(it, 1, span(0), topic, msgId)
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
        producerSpan(it, 1, span(0), topic, msgId)
        receiveSpan(it, 2, span(1), topic, msgId)
        processSpan(it, 3, span(2), topic, msgId)
      }
    }
  }

  def "test consume non-partitioned topic using receive"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceive"
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
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

    def receivedMsg = consumer.receive()
    consumer.acknowledge(receivedMsg)

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        producerSpan(it, 1, span(0), topic, msgId)
        receiveSpan(it, 2, span(1), topic, msgId)
      }
    }
  }

  def "test consume non-partitioned topic using receiveAsync"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveAsync"
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .subscribe()

    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()

    when:
    CompletableFuture<Message<String>> result = consumer.receiveAsync().whenComplete { receivedMsg, throwable ->
      runWithSpan("callback") {
        consumer.acknowledge(receivedMsg)
      }
    }

    def msg = "test"
    def msgId = runWithSpan("parent") {
      producer.send(msg)
    }

    result.get(1, TimeUnit.MINUTES)

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        producerSpan(it, 1, span(0), topic, msgId)
        receiveSpan(it, 2, span(1), topic, msgId)
        span(3) {
          name "callback"
          kind INTERNAL
          childOf span(2)
          attributes {
          }
        }
      }
    }
  }

  def "test consume non-partitioned topic using receive with timeout"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveWithTimeout"
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
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

    def receivedMsg = consumer.receive(1, TimeUnit.MINUTES)
    consumer.acknowledge(receivedMsg)

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        producerSpan(it, 1, span(0), topic, msgId)
        receiveSpan(it, 2, span(1), topic, msgId)
      }
    }
  }

  def "test consume non-partitioned topic using batchReceive"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceive"
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
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

    runWithSpan("receive-parent") {
      def receivedMsg = consumer.batchReceive()
      consumer.acknowledge(receivedMsg)
    }

    then:
    def producer
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        producerSpan(it, 1, span(0), topic, msgId)
        producer = span(1)
      }
      trace(1, 2) {
        span(0) {
          name "receive-parent"
          kind INTERNAL
          hasNoParent()
        }
        receiveSpan(it, 1, span(0), topic, null, producer)
      }
    }
  }

  def "test consume non-partitioned topic using batchReceiveAsync"() {
    setup:
    def topic = "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceiveAsync"
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
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

    CompletableFuture<Messages<String>> result = runWithSpan("receive-parent") {
      consumer.batchReceiveAsync().whenComplete { receivedMsg, throwable ->
        runWithSpan("callback") {
          consumer.acknowledge(receivedMsg)
        }
      }
    }
    result.get(1, TimeUnit.MINUTES).size() == 1

    then:
    def producer
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        producerSpan(it, 1, span(0), topic, msgId)
        producer = span(1)
      }
      trace(1, 3) {
        span(0) {
          name "receive-parent"
          kind INTERNAL
          hasNoParent()
        }
        receiveSpan(it, 1, span(0), topic, null, producer)
        span(2) {
          name "callback"
          kind INTERNAL
          childOf span(1)
          attributes {
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
        producerSpan(it, 1, span(0), topic, msgId, true)
        receiveSpan(it, 2, span(1), topic, msgId, null, true)
        processSpan(it, 3, span(2), topic, msgId, true)
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
        producerSpan(it, 1, span(0), topic, ~/${topic}-partition-.*publish/, { it.startsWith(topic) }, msgId)
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
        producerSpan(it, 1, span(0), topic, ~/${topic}-partition-.*publish/, { it.startsWith(topic) }, msgId)
        receiveSpan(it, 2, span(1), topic,  ~/${topic}-partition-.*receive/, { it.startsWith(topic) }, msgId)
        processSpan(it, 3, span(2), topic, ~/${topic}-partition-.*process/, { it.startsWith(topic) }, msgId)
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
          producerSpan(it, 1, span(0), topic, null, { it.startsWith(topicNamePrefix) }, String)
          receiveSpan(it, 2, span(1), topic, null, { it.startsWith(topicNamePrefix) }, String)
          processSpan(it, 3, span(2), topic, null, { it.startsWith(topicNamePrefix) }, String)
        }
      }
    }
  }

  def producerSpan(TraceAssert trace, int index, Object parentSpan, String topic, Object msgId, boolean headers = false) {
    producerSpan(trace, index, parentSpan, topic, null, { it == topic }, msgId, headers)
  }

  def producerSpan(TraceAssert trace, int index, Object parentSpan, String topic, Pattern namePattern, Closure destination, Object msgId, boolean headers = false) {
    trace.span(index) {
      if (namePattern != null) {
        name namePattern
      } else {
        name "$topic publish"
      }
      kind PRODUCER
      childOf parentSpan
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.NET_PEER_NAME" brokerHost
        "$SemanticAttributes.NET_PEER_PORT" brokerPort
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" destination
        if (msgId == String) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
        } else if (msgId != null) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "messaging.pulsar.message.type" "normal"
        if (headers) {
          "messaging.header.test_message_header" { it == ["test"] }
        }
      }
    }
  }

  def receiveSpan(TraceAssert trace, int index, Object parentSpan, String topic, Object msgId, Object linkedSpan = null, boolean headers = false) {
    receiveSpan(trace, index, parentSpan, topic, null, { it == topic }, msgId, linkedSpan, headers)
  }

  def receiveSpan(TraceAssert trace, int index, Object parentSpan, String topic, Pattern namePattern, Closure destination, Object msgId, Object linkedSpan = null, boolean headers = false) {
    trace.span(index) {
      if (namePattern != null) {
        name namePattern
      } else {
        name "$topic receive"
      }
      kind CONSUMER
      childOf parentSpan
      if (linkedSpan == null) {
        hasNoLinks()
      } else {
        hasLink linkedSpan
      }
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.NET_PEER_NAME" brokerHost
        "$SemanticAttributes.NET_PEER_PORT" brokerPort
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" destination
        if (msgId == String) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
        } else if (msgId != null) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGING_OPERATION" "receive"
        if (headers) {
          "messaging.header.test_message_header" { it == ["test"] }
        }
      }
    }
  }

  def processSpan(TraceAssert trace, int index, Object parentSpan, String topic, Object msgId, boolean headers = false) {
    processSpan(trace, index, parentSpan, topic, null, { it == topic }, msgId, headers)
  }

  def processSpan(TraceAssert trace, int index, Object parentSpan, String topic, Pattern namePattern, Closure destination, Object msgId, boolean headers = false) {
    trace.span(index) {
      if (namePattern != null) {
        name namePattern
      } else {
        name "$topic process"
      }
      kind INTERNAL
      childOf parentSpan
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" destination
        if (msgId == String) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" String
        } else if (msgId != null) {
          "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        }
        "$SemanticAttributes.MESSAGING_OPERATION" "process"
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        if (headers) {
          "messaging.header.test_message_header" { it == ["test"] }
        }
      }
    }
  }
}
