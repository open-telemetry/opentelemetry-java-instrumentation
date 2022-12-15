/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.SpanAssert
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.MessageListener
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionInitialPosition
import org.awaitility.Awaitility
import org.junit.Assert
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class PulsarClientTest extends AgentInstrumentationSpecification {

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
    pulsar = new PulsarContainer(DEFAULT_IMAGE_NAME);
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
    def topic = "persistent://public/default/testNonPartitionedTopic_" + UUID.randomUUID()
    admin.topics().createNonPartitionedTopic(topic)
    producer =
      client.newProducer(Schema.STRING).topic(topic)
        .enableBatching(false).create()

    String msg = UUID.randomUUID().toString()

    def msgId
    runWithSpan("parent") {
      msgId = producer.send(msg)
    }

    def traces = waitForTraces(1)
    Assert.assertEquals(traces.size(), 1)
    def spans = traces[0]
    Assert.assertEquals(spans.size(), 2)
    def parent = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("parent")
    }
    def producer = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("PRODUCER/SEND")
    }
    Assert.assertNotNull(parent)
    Assert.assertNotNull(producer)

    SpanAssert.assertSpan(parent) {
      name("parent")
      kind(INTERNAL)
      hasNoParent()
    }

    SpanAssert.assertSpan(producer) {
      name("PRODUCER/SEND")
      kind(PRODUCER)
      childOf parent
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_DESTINATION" topic
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
      }
    }
  }

  def "test consume non-partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testNonPartitionedTopic_" + UUID.randomUUID()
    admin.topics().createNonPartitionedTopic(topic)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .topic(topic)
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
        }
      })
      .subscribe()

    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()

    def msgId
    def msg = UUID.randomUUID().toString()
    runWithSpan("parent") {
      msgId = producer.send(msg)
    }

    def traces = waitForTraces(1)
    def spans = traces[0]
    Awaitility.waitAtMost(1, TimeUnit.MINUTES).until(() -> spans.size() == 4)
    Assert.assertEquals(spans.size(), 4)
    def parent = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("parent")
    }
    def send = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("PRODUCER/SEND")
    }
    def receive = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/RECEIVE")
    }

    def process = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/PROCESS")
    }

    SpanAssert.assertSpan(parent) {
      name("parent")
      kind(INTERNAL)
      hasNoParent()
    }

    SpanAssert.assertSpan(send) {
      name("PRODUCER/SEND")
      kind(PRODUCER)
      childOf parent
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGING_DESTINATION" topic
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
      }
    }

    SpanAssert.assertSpan(receive) {
      name("CONSUMER/RECEIVE")
      kind(CONSUMER)
      childOf(send)
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGING_DESTINATION" topic
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGING_OPERATION" "receive"
      }
    }

    SpanAssert.assertSpan(process) {
      name("CONSUMER/PROCESS")
      kind(INTERNAL)
      childOf(receive)
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_DESTINATION" topic
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_OPERATION" "process"
      }
    }
  }


  def "test send partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testPartitionedTopic_" + UUID.randomUUID()
    admin.topics().createPartitionedTopic(topic, 2)
    producer =
      client.newProducer(Schema.STRING).topic(topic)
        .enableBatching(false).create()

    String msg = UUID.randomUUID().toString()

    def msgId
    runWithSpan("parent") {
      msgId = producer.send(msg)
    }

    def traces = waitForTraces(1)
    def spans = traces[0]
    Assert.assertEquals(spans.size(), 2)

    def parent = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("parent")
    }
    def send = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("PRODUCER/SEND")
    }

    SpanAssert.assertSpan(parent) {
      name("parent")
      kind(INTERNAL)
      hasNoParent()
    }

    SpanAssert.assertSpan(send) {
      name("PRODUCER/SEND")
      kind(PRODUCER)
      childOf parent
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGING_DESTINATION" {
          t ->
            return t.toString().contains(topic)
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
      }
    }
  }

  def "test consume partitioned topic"() {
    setup:
    def topic = "persistent://public/default/testPartitionedTopic_" + UUID.randomUUID()
    admin.topics().createPartitionedTopic(topic, 2)
    consumer = client.newConsumer(Schema.STRING)
      .subscriptionName("test_sub")
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .topic(topic)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
        }
      })
      .subscribe()

    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()

    def msgId
    def msg = UUID.randomUUID().toString()
    runWithSpan("parent") {
      msgId = producer.send(msg)
    }

    def traces = waitForTraces(1)
    Assert.assertEquals(traces.size(), 1)
    def spans = traces[0]
    Awaitility.waitAtMost(1, TimeUnit.MINUTES).until(() -> spans.size() == 4)
    Assert.assertEquals(spans.size(), 4)

    def parent = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("parent")
    }
    def send = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("PRODUCER/SEND")
    }
    def receive = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/RECEIVE")
    }

    def process = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/PROCESS")
    }

    SpanAssert.assertSpan(parent) {
      name("parent")
      kind(INTERNAL)
      hasNoParent()
    }

    SpanAssert.assertSpan(send) {
      name("PRODUCER/SEND")
      kind(PRODUCER)
      childOf parent
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGING_DESTINATION" {
          v ->
            return v.toString().contains(topic)
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGE_TYPE" "NORMAL"
      }
    }

    SpanAssert.assertSpan(receive) {
      name("CONSUMER/RECEIVE")
      kind(CONSUMER)
      childOf(send)
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_URL" brokerUrl
        "$SemanticAttributes.MESSAGING_DESTINATION" {
          v ->
            return v.toString().contains(topic)
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGING_OPERATION" "RECEIVE"
      }
    }

    SpanAssert.assertSpan(process) {
      name("CONSUMER/PROCESS")
      kind(INTERNAL)
      childOf(receive)
      attributes {
        "$SemanticAttributes.MESSAGING_SYSTEM" "pulsar"
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "topic"
        "$SemanticAttributes.MESSAGING_DESTINATION" {
          v ->
            return v.toString().contains(topic)
        }
        "$SemanticAttributes.MESSAGING_MESSAGE_ID" msgId.toString()
        "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
        "$SemanticAttributes.MESSAGING_OPERATION" "PROCESS"
      }
    }
  }


  def "test consume multi-topics"() {
    setup:

    def topic = "persistent://public/default/testNonPartitionedTopic_" + UUID.randomUUID()
    def topic1 = "persistent://public/default/testNonPartitionedTopic_" + UUID.randomUUID()
    producer = client.newProducer(Schema.STRING)
      .topic(topic)
      .enableBatching(false)
      .create()
    producer1 = client.newProducer(Schema.STRING)
      .topic(topic1)
      .enableBatching(false)
      .create()

    runWithSpan("parent") {
      producer.send(UUID.randomUUID().toString())
      producer1.send(UUID.randomUUID().toString())
    }

    consumer = client.newConsumer(Schema.STRING)
      .topic(topic1, topic)
      .subscriptionName("test_sub")
      .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
      .messageListener(new MessageListener<String>() {
        @Override
        void received(Consumer<String> consumer, Message<String> msg) {
          consumer.acknowledge(msg)
        }
      })
      .subscribe()

    def traces = waitForTraces(1)
    Assert.assertEquals(traces.size(), 1)
    def spans = traces[0]
    Awaitility.waitAtMost(1, TimeUnit.MINUTES).until(() -> spans.size() == 7)
    Assert.assertEquals(spans.size(), 7)

    def parent = spans.find {
      it0 ->
        it0.name.equalsIgnoreCase("parent")
    }


    def sendSpans = spans.findAll {
      it0 ->
        it0.name.equalsIgnoreCase("PRODUCER/SEND")
    }

    sendSpans.forEach {
      it0 ->
        SpanAssert.assertSpan(it0) {
          childOf(parent)
        }
    }

    def receiveSpans = spans.findAll {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/RECEIVE")
    }

    def processSpans = spans.findAll {
      it0 ->
        it0.name.equalsIgnoreCase("CONSUMER/PROCESS")
    }

    receiveSpans.forEach {
      it0 ->
        def parentSpanId = it0.getParentSpanId()
        def parent0 = sendSpans.find {
          v ->
            (v.spanId == parentSpanId)
        }

        SpanAssert.assertSpan(it0) {
          childOf(parent0)
        }
    }

    processSpans.forEach {
      it0 ->
        def parentSpanId = it0.getParentSpanId()
        def parent0 = processSpans.find {
          v ->
            (v.spanId == parentSpanId)
        }

        SpanAssert.assertSpan(it0) {
          childOf(parent0)
        }
    }
  }
}
