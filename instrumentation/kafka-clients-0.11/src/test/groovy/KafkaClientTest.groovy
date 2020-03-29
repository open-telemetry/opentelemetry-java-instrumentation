/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.junit.Rule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

class KafkaClientTest extends AgentTestRunner {
  static final SHARED_TOPIC = "shared.topic"

  @Rule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, SHARED_TOPIC)

  def "test kafka produce and consume"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

    // create a Kafka consumer factory
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProperties)

    // set the topic that needs to be consumed
    def containerProperties
    try {
      // Different class names for test and latestDepTest.
      containerProperties = Class.forName("org.springframework.kafka.listener.config.ContainerProperties").newInstance(SHARED_TOPIC)
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      containerProperties = Class.forName("org.springframework.kafka.listener.ContainerProperties").newInstance(SHARED_TOPIC)
    }

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
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    String greeting = "Hello Spring Kafka Sender!"
    kafkaTemplate.send(SHARED_TOPIC, greeting)


    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == greeting
    received.key() == null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(0)
          tags {
            "partition" { it >= 0 }
            "offset" 0
          }
        }
      }
    }

    cleanup:
    producerFactory.stop()
    container?.stop()
  }

  def "test records(TopicPartition) kafka consume"() {
    setup:

    // set up the Kafka consumer properties
    def kafkaPartition = 0
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    def consumer = new KafkaConsumer<String, String>(consumerProperties)

    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producer = new KafkaProducer(senderProps)

    consumer.assign(Arrays.asList(new TopicPartition(SHARED_TOPIC, kafkaPartition)))

    when:
    def greeting = "Hello from MockConsumer!"
    producer.send(new ProducerRecord<Integer, String>(SHARED_TOPIC, kafkaPartition, null, greeting))

    then:
    TEST_WRITER.waitForTraces(1)
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()
    def pollResult = KafkaTestUtils.getRecords(consumer)

    def recs = pollResult.records(new TopicPartition(SHARED_TOPIC, kafkaPartition)).iterator()

    def first = null
    if (recs.hasNext()) {
      first = recs.next()
    }

    then:
    recs.hasNext() == false
    first.value() == greeting
    first.key() == null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          tags {
            "kafka.partition" { it >= 0 }
          }
        }
        span(1) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(0)
          tags {
            "partition" { it >= 0 }
            "offset" 0
          }
        }
      }
    }

    cleanup:
    consumer.close()
    producer.close()

  }

}
