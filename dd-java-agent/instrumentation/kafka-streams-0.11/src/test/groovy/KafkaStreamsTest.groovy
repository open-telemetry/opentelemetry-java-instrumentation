import datadog.trace.agent.test.AgentTestRunner
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.ValueMapper
import org.junit.ClassRule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.lang.Shared

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class KafkaStreamsTest extends AgentTestRunner {
  static final STREAM_PENDING = "test.pending"
  static final STREAM_PROCESSED = "test.processed"

  @Shared
  @ClassRule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, STREAM_PENDING, STREAM_PROCESSED)

  def "test kafka produce and consume with streams in-between"() {
    setup:
    def config = new Properties()
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    config.putAll(senderProps)
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())

    // CONFIGURE CONSUMER
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(KafkaTestUtils.consumerProps("sender", "false", embeddedKafka))

    def containerProperties
    try {
      // Different class names for test and latestDepTest.
      containerProperties = Class.forName("org.springframework.kafka.listener.config.ContainerProperties").newInstance(STREAM_PROCESSED)
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      containerProperties = Class.forName("org.springframework.kafka.listener.ContainerProperties").newInstance(STREAM_PROCESSED)
    }
    def consumerContainer = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties)

    // create a thread safe queue to store the processed message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()

    // setup a Kafka message listener
    consumerContainer.setupMessageListener(new MessageListener<String, String>() {
      @Override
      void onMessage(ConsumerRecord<String, String> record) {
        // ensure consistent ordering of traces
        // this is the last processing step so we should see 2 traces here
        TEST_WRITER.waitForTraces(2)
        getTestTracer().activeSpan().setTag("testing", 123)
        records.add(record)
      }
    })

    // start the container and underlying message listener
    consumerContainer.start()

    // wait until the container has the required number of assigned partitions
    ContainerTestUtils.waitForAssignment(consumerContainer, embeddedKafka.getPartitionsPerTopic())

    // CONFIGURE PROCESSOR
    def builder
    try {
      // Different class names for test and latestDepTest.
      builder = Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder").newInstance()
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      builder = Class.forName("org.apache.kafka.streams.StreamsBuilder").newInstance()
    }
    KStream<String, String> textLines = builder.stream(STREAM_PENDING)
    def values = textLines
      .mapValues(new ValueMapper<String, String>() {
      @Override
      String apply(String textLine) {
        TEST_WRITER.waitForTraces(1) // ensure consistent ordering of traces
        getTestTracer().activeSpan().setTag("asdf", "testing")
        return textLine.toLowerCase()
      }
    })

    KafkaStreams streams
    try {
      // Different api for test and latestDepTest.
      values.to(Serdes.String(), Serdes.String(), STREAM_PROCESSED)
      streams = new KafkaStreams(builder, config)
    } catch (MissingMethodException e) {
      def producer = Class.forName("org.apache.kafka.streams.kstream.Produced")
        .with(Serdes.String(), Serdes.String())
      values.to(STREAM_PROCESSED, producer)
      streams = new KafkaStreams(builder.build(), config)
    }
    streams.start()

    // CONFIGURE PRODUCER
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    when:
    String greeting = "TESTING TESTING 123!"
    kafkaTemplate.send(STREAM_PENDING, greeting)

    then:
    // check that the message was received
    def received = records.poll(10, TimeUnit.SECONDS)
    received.value() == greeting.toLowerCase()
    received.key() == null

    assertTraces(3) {
      trace(0, 1) {
        // PRODUCER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.produce"
          resourceName "Produce Topic $STREAM_PENDING"
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
      trace(1, 2) {

        // STREAMING span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.produce"
          resourceName "Produce Topic $STREAM_PROCESSED"
          spanType "queue"
          errored false
          childOf span(1)

          tags {
            "component" "java-kafka"
            "span.kind" "producer"
            defaultTags()
          }
        }

        // STREAMING span 1
        span(1) {
          serviceName "kafka"
          operationName "kafka.consume"
          resourceName "Consume Topic $STREAM_PENDING"
          spanType "queue"
          errored false
          childOf TEST_WRITER[0][0]

          tags {
            "component" "java-kafka"
            "span.kind" "consumer"
            "partition" { it >= 0 }
            "offset" 0
            defaultTags(true)
            "asdf" "testing"
          }
        }
      }
      trace(2, 1) {
        // CONSUMER span 0
        span(0) {
          serviceName "kafka"
          operationName "kafka.consume"
          resourceName "Consume Topic $STREAM_PROCESSED"
          spanType "queue"
          errored false
          childOf TEST_WRITER[1][0]
          tags {
            "component" "java-kafka"
            "span.kind" "consumer"
            "partition" { it >= 0 }
            "offset" 0
            defaultTags(true)
            "testing" 123
          }
        }
      }
    }

    def headers = received.headers()
    headers.iterator().hasNext()
    new String(headers.headers("x-datadog-trace-id").iterator().next().value()) == "${TEST_WRITER[1][0].traceId}"
    new String(headers.headers("x-datadog-parent-id").iterator().next().value()) == "${TEST_WRITER[1][0].spanId}"


    cleanup:
    producerFactory?.stop()
    streams?.close()
    consumerContainer?.stop()
  }
}
