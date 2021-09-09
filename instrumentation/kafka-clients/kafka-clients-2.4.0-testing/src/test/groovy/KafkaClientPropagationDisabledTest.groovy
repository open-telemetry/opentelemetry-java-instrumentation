/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener

class KafkaClientPropagationDisabledTest extends KafkaClientBaseTest {

  def "should not read remote context when consuming messages if propagation is disabled"() {
    setup:
    def senderProps = senderProps()
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    when: "send message"
    String message = "Testing without headers"
    kafkaTemplate.send(SHARED_TOPIC, message)

    then: "producer span is created"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
      }
    }

    when: "read message without context propagation"
    // create a thread safe queue to store the received message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()
    KafkaMessageListenerContainer<Object, Object> container = startConsumer("consumer-without-propagation", records)

    then: "independent consumer span is created"
    // check that the message was received
    records.poll(5, TimeUnit.SECONDS) != null

    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name SHARED_TOPIC + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
      }

    }

    cleanup:
    stopProducerFactory(producerFactory)
    container?.stop()
  }

  protected KafkaMessageListenerContainer<Object, Object> startConsumer(String groupId, records) {
    // set up the Kafka consumer properties
    Map<String, Object> consumerProperties = consumerProps(groupId, "false")
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    // create a Kafka consumer factory
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProperties)

    // set the topic that needs to be consumed
    def containerProperties = containerProperties()

    // create a Kafka MessageListenerContainer
    def container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties)

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
    container
  }

  @Override
  def containerProperties() {
    try {
      // Different class names for test and latestDepTest.
      return Class.forName("org.springframework.kafka.listener.config.ContainerProperties").newInstance(SHARED_TOPIC)
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return Class.forName("org.springframework.kafka.listener.ContainerProperties").newInstance(SHARED_TOPIC)
    }
  }
}
