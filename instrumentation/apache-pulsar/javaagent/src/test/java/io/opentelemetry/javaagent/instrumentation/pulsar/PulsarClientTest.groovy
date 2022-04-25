package io.opentelemetry.javaagent.instrumentation.pulsar

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.MessageListener
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.PulsarClient
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.utility.DockerImageName

import java.nio.charset.Charset

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class PulsarClientTest extends AgentInstrumentationSpecification {

  private static final DockerImageName DEFAULT_IMAGE_NAME =
    DockerImageName.parse("apachepulsar/pulsar:2.8.0")

  PulsarContainer pulsar
  Producer<byte[]> producer
  Consumer<byte[]> consumer

  @Override
  def setupSpec() {
    PulsarContainer pulsar = new PulsarContainer(DEFAULT_IMAGE_NAME);
    pulsar.start()

    def url = pulsar.pulsarBrokerUrl
    def topic = "persistent://public/default/test_opentelemetry" + UUID.randomUUID().toString()

    def client = PulsarClient.builder().serviceUrl(url).build()

    this.producer = client.newProducer().topic(topic).create()
    this.consumer = client.newConsumer()
      .topic(topic)
      .subscriptionName("test_sub")
      .messageListener(new MessageListener<byte[]>() {
        @Override
        void received(Consumer<byte[]> consumer, Message<byte[]> msg) {
          consumer.acknowledge(msg)
        }
      })
      .subscribe()
  }

  @Override
  def cleanupSpec() {
    producer?.close()
    consumer?.close()
    pulsar.close()
  }

  def "test producer send message"() {
    setup:
    runWithSpan("parent") {
      producer.send(UUID.randomUUID().toString().getBytes(Charset.defaultCharset()))
    }

    print('------apache-pulsar------')
    print(traces.toString())
  }
}
