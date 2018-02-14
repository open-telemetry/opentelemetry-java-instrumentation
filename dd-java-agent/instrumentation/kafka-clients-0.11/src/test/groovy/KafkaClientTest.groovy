import datadog.trace.agent.test.AgentTestRunner
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.ClassRule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.listener.config.ContainerProperties
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.lang.Shared

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class KafkaClientTest extends AgentTestRunner {
  static final SHARED_TOPIC = "shared.topic"

  static {
    System.setProperty("dd.integration.kafka.enabled", "true")
  }

  @Shared
  @ClassRule
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
    ContainerProperties containerProperties = new ContainerProperties(SHARED_TOPIC)

    // create a Kafka MessageListenerContainer
    def container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties)

    // create a thread safe queue to store the received message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()

    // setup a Kafka message listener
    WRITER_PHASER.register()
    container.setupMessageListener(new MessageListener<String, String>() {
      @Override
      void onMessage(ConsumerRecord<String, String> record) {
        WRITER_PHASER.arriveAndAwaitAdvance() // ensure consistent ordering of traces
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

    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.size() == 2

    def t1 = TEST_WRITER.get(0)
    t1.size() == 1
    def t2 = TEST_WRITER.get(1)
    t2.size() == 1

    and: // PRODUCER span 0
    def t1span1 = t1[0]

    t1span1.context().operationName == "kafka.produce"
    t1span1.serviceName == "kafka"
    t1span1.resourceName == "Produce Topic $SHARED_TOPIC"
    t1span1.type == "queue"
    !t1span1.context().getErrorFlag()
    t1span1.context().parentId == 0

    def t1tags1 = t1span1.context().tags
    t1tags1["component"] == "java-kafka"
    t1tags1["span.kind"] == "producer"
    t1tags1["span.type"] == "queue"
    t1tags1["thread.name"] != null
    t1tags1["thread.id"] != null
    t1tags1.size() == 5

    and: // CONSUMER span 0
    def t2span1 = t2[0]

    t2span1.context().operationName == "kafka.consume"
    t2span1.serviceName == "kafka"
    t2span1.resourceName == "Consume Topic $SHARED_TOPIC"
    t2span1.type == "queue"
    !t2span1.context().getErrorFlag()
    t2span1.context().parentId == t1span1.context().spanId

    def t2tags1 = t2span1.context().tags
    t2tags1["component"] == "java-kafka"
    t2tags1["span.kind"] == "consumer"
    t1tags1["span.type"] == "queue"
    t2tags1["partition"] >= 0
    t2tags1["offset"] == 0
    t2tags1["thread.name"] != null
    t2tags1["thread.id"] != null
    t2tags1.size() == 7

    def headers = received.headers()
    headers.iterator().hasNext()
    new String(headers.headers("x-datadog-trace-id").iterator().next().value()) == "$t1span1.traceId"
    new String(headers.headers("x-datadog-parent-id").iterator().next().value()) == "$t1span1.spanId"


    cleanup:
    producerFactory.stop()
    container.stop()
  }

}
