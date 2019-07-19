import datadog.trace.agent.test.AgentTestRunner
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
    kafkaTemplate.send(SHARED_TOPIC, greeting)


    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == greeting
    received.key() == null

    assertTraces(2) {
      trace(0, 1) {
        // PRODUCER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.produce"
          resourceName "Produce Topic $SHARED_TOPIC"
          spanType "queue"
          errored false
          parent()
          tags {
            "component" "java-kafka"
            "span.kind" "producer"
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        // CONSUMER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.consume"
          resourceName "Consume Topic $SHARED_TOPIC"
          spanType "queue"
          errored false
          childOf TEST_WRITER[0][0]
          tags {
            "component" "java-kafka"
            "span.kind" "consumer"
            "partition" { it >= 0 }
            "offset" 0
            defaultTags(true)
          }
        }
      }
    }

    def headers = received.headers()
    headers.iterator().hasNext()
    new String(headers.headers("x-datadog-trace-id").iterator().next().value()) == "${TEST_WRITER[0][0].traceId}"
    new String(headers.headers("x-datadog-parent-id").iterator().next().value()) == "${TEST_WRITER[0][0].spanId}"

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
    def consumer = new KafkaConsumer<String,String>(consumerProperties)

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

    assertTraces(2) {
      trace(0, 1) {
        // PRODUCER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.produce"
          resourceName "Produce Topic $SHARED_TOPIC"
          spanType "queue"
          errored false
          parent()
          tags {
            "component" "java-kafka"
            "span.kind" "producer"
            "kafka.partition" { it >= 0 }
            defaultTags(true)
          }
        }
      }
      trace(1, 1) {
        // CONSUMER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.consume"
          resourceName "Consume Topic $SHARED_TOPIC"
          spanType "queue"
          errored false
          childOf TEST_WRITER[0][0]
          tags {
            "component" "java-kafka"
            "span.kind" "consumer"
            "partition" { it >= 0 }
            "offset" 0
            defaultTags(true)
          }
        }
      }
    }

    cleanup:
    consumer.close()
    producer.close()

  }

}
