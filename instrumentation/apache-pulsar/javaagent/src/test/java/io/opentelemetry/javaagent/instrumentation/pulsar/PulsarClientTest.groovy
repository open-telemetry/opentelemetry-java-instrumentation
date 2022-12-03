/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.MessageListener
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.utility.DockerImageName

class PulsarClientTest extends AgentInstrumentationSpecification {

  private static final DockerImageName DEFAULT_IMAGE_NAME =
    DockerImageName.parse("apachepulsar/pulsar:2.8.0")

  PulsarContainer pulsar
  PulsarClient client
  String topic
  Producer<byte[]> producer
  Consumer<byte[]> consumer

  @Override
  def setupSpec() {
    PulsarContainer pulsar = new PulsarContainer(DEFAULT_IMAGE_NAME);
    pulsar.start()

    def url = pulsar.pulsarBrokerUrl
    topic = "persistent://public/default/test_" + UUID.randomUUID().toString()
    client = PulsarClient.builder().serviceUrl(url).build()
    this.producer = client.newProducer().topic(topic).create()
  }

  @Override
  def cleanupSpec() {
    producer?.close()
    consumer?.close()
    client?.close()
    pulsar.close()
  }

  def "test producer send message"() {
    setup:
    runWithSpan("parent") {
      producer.send(UUID.randomUUID().toString().getBytes(Charset.defaultCharset()))
    }

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name("parent")
          kind(INTERNAL)
          hasNoParent()
        }

        span(1) {
          name("PRODUCER/SEND")
          kind(PRODUCER)
          childOf span(0)
        }
      }
    }
  }

  def "test send and consume message"() {
    setup:
    def latch = new CountDownLatch(1)
    consumer = client.newConsumer(Schema.BYTES)
      .subscriptionName("test_sub")
      .messageListener(new MessageListener<byte[]>() {
        @Override
        void received(Consumer<byte[]> consumer, Message<byte[]> msg) {
          latch.countDown()
          consumer.acknowledge(msg)
        }
      })
      .subscribe()

    runWithSpan("parent") {
      producer.send(UUID.randomUUID().toString().getBytes(Charset.defaultCharset()))
    }

    latch.await(1, TimeUnit.MINUTES)

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name("parent")
          kind(INTERNAL)
          hasNoParent()
        }

        span(1) {
          name("PRODUCER/SEND")
          kind(PRODUCER)
          childOf span(0)
        }

        span(2) {
          name("CONSUMER/RECEIVE")
          kind(CONSUMER)
          childOf span(1)
        }

        span(3) {
          name("CONSUMER/PROCESS")
          kind(INTERNAL)
          childOf(span(2))
        }
      }
    }
  }
}
