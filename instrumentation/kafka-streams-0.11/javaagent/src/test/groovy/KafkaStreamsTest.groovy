/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
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

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class KafkaStreamsTest extends AgentInstrumentationSpecification {

  static final STREAM_PENDING = "test.pending"
  static final STREAM_PROCESSED = "test.processed"

  @Shared
  @ClassRule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, STREAM_PENDING, STREAM_PROCESSED)

  Map<String, Object> senderProps() {
    return KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
  }

  Map<String, Object> consumerProps(String group, String autoCommit) {
    return KafkaTestUtils.consumerProps(group, autoCommit, embeddedKafka)
  }

  void waitForAssignment(Object container) {
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())
  }

  def stopProducerFactory(DefaultKafkaProducerFactory producerFactory) {
    producerFactory.stop()
  }

  def "test kafka produce and consume with streams in-between"() {
    setup:
    def config = new Properties()
    def senderProps = senderProps()
    config.putAll(senderProps)
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())

    // CONFIGURE CONSUMER
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProps("sender", "false"))

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
        Span.current().setAttribute("testing", 123)
        records.add(record)
      }
    })

    // start the container and underlying message listener
    consumerContainer.start()

    // wait until the container has the required number of assigned partitions
    waitForAssignment(consumerContainer)

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
          Span.current().setAttribute("asdf", "testing")
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

    assertTraces(1) {
      trace(0, 5) {
        // PRODUCER span 0
        span(0) {
          name STREAM_PENDING + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        // CONSUMER span 0
        span(1) {
          name STREAM_PENDING + " process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
          }
        }
        // STREAMING span 1
        span(2) {
          name STREAM_PENDING + " process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "asdf" "testing"
          }
        }
        // STREAMING span 0
        span(3) {
          name STREAM_PROCESSED + " send"
          kind PRODUCER
          childOf span(2)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        // CONSUMER span 0
        span(4) {
          name STREAM_PROCESSED + " process"
          kind CONSUMER
          childOf span(3)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
            "testing" 123
          }
        }
      }
    }

    def headers = received.headers()
    headers.iterator().hasNext()
    def traceparent = new String(headers.headers("traceparent").iterator().next().value())
    Context context = W3CTraceContextPropagator.instance.extract(Context.root(), "", new TextMapGetter<String>() {
      @Override
      Iterable<String> keys(String carrier) {
        return Collections.singleton("traceparent")
      }

      @Override
      String get(String carrier, String key) {
        if (key == "traceparent") {
          return traceparent
        }
        return null
      }
    })
    def spanContext = Span.fromContext(context).getSpanContext()
    def streamSendSpan = traces[0][3]
    spanContext.traceId == streamSendSpan.traceId
    spanContext.spanId == streamSendSpan.spanId


    cleanup:
    stopProducerFactory(producerFactory)
    streams?.close()
    consumerContainer?.stop()
  }
}
