/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.Rule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.rule.EmbeddedKafkaRule
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.lang.Unroll

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

abstract class KafkaClientBaseTest extends AgentInstrumentationSpecification {

  protected static final SHARED_TOPIC = "shared.topic"

  private static final boolean propagationEnabled = Boolean.parseBoolean(
    System.getProperty("otel.instrumentation.kafka.client-propagation.enabled", "true"))

  @Rule
  EmbeddedKafkaRule embeddedKafka = new EmbeddedKafkaRule(1, true, SHARED_TOPIC)

  abstract containerProperties()

  Map<String, Object> senderProps() {
    return KafkaTestUtils.producerProps(embeddedKafka.getEmbeddedKafka().getBrokersAsString())
  }

  Map<String, Object> consumerProps(String group, String autoCommit) {
    return KafkaTestUtils.consumerProps(group, autoCommit, embeddedKafka.getEmbeddedKafka())
  }

  void waitForAssignment(Object container) {
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getEmbeddedKafka().getPartitionsPerTopic())
  }

  def stopProducerFactory(DefaultKafkaProducerFactory producerFactory) {
    producerFactory.destroy()
  }

  @Unroll
  def "test kafka client header propagation manual config"() {
    setup:
    def senderProps = senderProps()
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = consumerProps("sender", "false")

    // create a Kafka consumer factory
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProperties)

    // set the topic that needs to be consumed
    def containerProperties = containerProperties()

    // create a Kafka MessageListenerContainer
    def container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties)

    // create a thread safe queue to store the received message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()

    // setup a Kafka message listener
    container.setupMessageListener(new MessageListener<String, String>() {
      @Override
      void onMessage(ConsumerRecord<String, String> record) {
        records.add(record)
      }
    })

    // start the container and underlying message listener
    container.start()

    // wait until the container has the required number of assigned partitions
    waitForAssignment(container)

    when:
    String message = "Testing without headers"
    kafkaTemplate.send(SHARED_TOPIC, message)

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)

    received.headers().iterator().hasNext() == propagationEnabled

    cleanup:
    stopProducerFactory(producerFactory)
    container?.stop()
  }
}
