/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.trace.attributes.SemanticAttributes
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

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.setConfig
import static io.opentelemetry.instrumentation.test.utils.ConfigUtils.updateConfig
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

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
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(2) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
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
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(2) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
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

    assertTraces(1) {
      trace(0, 2) {
        // PRODUCER span 0
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "tombstone" true
          }
        }
        // CONSUMER span 0
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
            "tombstone" true
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
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "partition" { it >= 0 }
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
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
    def previousConfig = setPropagation(propagationEnabled)
    kafkaTemplate.send(SHARED_TOPIC, message)
    setConfig(previousConfig)

    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)

    received.headers().iterator().hasNext() == propagationEnabled

    cleanup:
    producerFactory.stop()
    container?.stop()

    where:
    propagationEnabled << [false, true]
  }

  def "should not read remote context when consuming messages if propagation is disabled"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
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
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
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
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }
    }
    container.stop()

    when: "read message without context propagation"
    def previousConfig = setPropagation(false)
    records.clear()
    container = startConsumer("consumer-without-propagation", records)

    then: "independent consumer span is created"
    // check that the message was received
    records.poll(5, TimeUnit.SECONDS) != null

    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          name SHARED_TOPIC + " send"
          kind PRODUCER
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        span(1) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "partition" { it >= 0 }
            "offset" 0
            "record.queue_time_ms" { it >= 0 }
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name SHARED_TOPIC + " process"
          kind CONSUMER
          errored false
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" SHARED_TOPIC
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
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
    setConfig(previousConfig)
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

  private static Config setPropagation(boolean propagationEnabled) {
    return updateConfig {
      it.setProperty("otel.kafka.client.propagation.enabled", Boolean.toString(propagationEnabled))
    }
  }
}
