/*
 * Copyright The OpenTelemetry Authors
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

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.instrumentation.api.config.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.Rule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.lang.Unroll

class KafkaClientTest extends AgentTestRunner {
  static final SHARED_TOPIC = "shared.topic"

  @Rule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, SHARED_TOPIC)

  def "test kafka produce and consume"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    Producer<String, String> producer = new KafkaProducer<>(senderProps, new StringSerializer(), new StringSerializer())

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

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
        TEST_WRITER.waitForTraces(1) // ensure consistent ordering of traces
        records.add(record)
      }
    })

    // start the container and underlying message listener
    container.start()

    // wait until the container has the required number of assigned partitions
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    String greeting = "Hello Spring Kafka Sender!"
    runUnderTrace("parent") {
      producer.send(new ProducerRecord(SHARED_TOPIC, greeting)) { meta, ex ->
        if (ex == null) {
          runUnderTrace("producer callback") {}
        } else {
          runUnderTrace("producer exception: " + ex) {}
        }
      }
    }

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == greeting
    received.key() == null

    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(1)
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
        basicSpan(it, 3, "producer callback", span(0))
      }
    }

    cleanup:
    producer.close()
    container?.stop()
  }

  def "test spring kafka template produce and consume"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

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
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    String greeting = "Hello Spring Kafka Sender!"
    runUnderTrace("parent") {
      kafkaTemplate.send(SHARED_TOPIC, greeting).addCallback({
        runUnderTrace("producer callback") {}
      }, { ex ->
        runUnderTrace("producer exception: " + ex) {}
      })
    }

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == greeting
    received.key() == null

    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(1)
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
        basicSpan(it, 3, "producer callback", span(0))
      }
    }

    cleanup:
    producerFactory.stop()
    container?.stop()
  }

  def "test pass through tombstone"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

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
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    kafkaTemplate.send(SHARED_TOPIC, null)

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == null
    received.key() == null

    assertTraces(2) {
      trace(0, 1) {
        // PRODUCER span 0
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          attributes {
            "tombstone" true
          }
        }
      }
      // when a user consumes a tombstone a new trace is started
      // because context can't be propagated safely
      trace(1, 1) {
        // CONSUMER span 0
        span(0) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          parent()
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
            "tombstone" true
          }
        }
      }
    }

    def headers = received.headers()
    !headers.iterator().hasNext()

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
          attributes {
            "partition" { it >= 0 }
          }
        }
        span(1) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }
    }

    cleanup:
    consumer.close()
    producer.close()
  }

  @Unroll
  def "test kafka client header propagation manual config"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

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
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    String message = "Testing without headers"
    withConfigOverride(Config.KAFKA_CLIENT_PROPAGATION_ENABLED, value) {
      kafkaTemplate.send(SHARED_TOPIC, message)
    }

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)

    received.headers().iterator().hasNext() == expected

    cleanup:
    producerFactory.stop()
    container?.stop()

    where:
    value   | expected
    "false" | false
    "true"  | true
  }

  def "should not read remote context when consuming messages if propagation is disabled"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    when: "send message"
    String message = "Testing without headers"
    withConfigOverride(Config.KAFKA_CLIENT_PROPAGATION_ENABLED, "true") {
      kafkaTemplate.send(SHARED_TOPIC, message)
    }

    then: "producer span is created"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          attributes {
          }
        }
      }
    }

    when: "read message with context propagation"
    // create a thread safe queue to store the received message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()
    KafkaMessageListenerContainer<Object, Object> container = startConsumer("consumer-with-propagation", records)

    then: "consumer span is created and is a child of the producer"
    // check that the message was received
    records.poll(5, TimeUnit.SECONDS) != null

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          attributes {
          }
        }
        span(1) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }
    }
    container.stop()

    when: "read message without context propagation"
    ConfigUtils.updateConfig {
      System.setProperty("otel." + Config.KAFKA_CLIENT_PROPAGATION_ENABLED, "false")
    }
    records.clear()
    container = startConsumer("consumer-without-propagation", records)

    then: "independent consumer span is created"
    // check that the message was received
    records.poll(5, TimeUnit.SECONDS) != null

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind PRODUCER
          errored false
          parent()
          attributes {
          }
        }
        span(1) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName SHARED_TOPIC
          spanKind CONSUMER
          errored false
          parent()
          attributes {
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }

    }

    cleanup:
    producerFactory.stop()
    container?.stop()
    ConfigUtils.updateConfig {
      System.clearProperty("otel." + Config.KAFKA_CLIENT_PROPAGATION_ENABLED)
    }
  }

  protected KafkaMessageListenerContainer<Object, Object> startConsumer(String groupId, records) {
    // set up the Kafka consumer properties
    Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(groupId, "false", embeddedKafka)
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
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())
    container
  }


  def containerProperties() {
    try {
      // Different class names for test and latestDepTest.
      return Class.forName("org.springframework.kafka.listener.config.ContainerProperties").newInstance(SHARED_TOPIC)
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return Class.forName("org.springframework.kafka.listener.ContainerProperties").newInstance(SHARED_TOPIC)
    }
  }

}
