/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.ValueMapper
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class KafkaStreamsTest extends AgentInstrumentationSpecification {

  static final STREAM_PENDING = "test.pending"
  static final STREAM_PROCESSED = "test.processed"

  @Shared
  static KafkaContainer kafka
  @Shared
  static Producer<Integer, String> producer
  @Shared
  static Consumer<Integer, String> consumer
  @Shared
  static CountDownLatch consumerReady = new CountDownLatch(1)

  def setupSpec() {
    kafka = new KafkaContainer()
    kafka.start()

    // create test topic
    AdminClient.create(["bootstrap.servers": kafka.bootstrapServers]).withCloseable { admin ->
      admin.createTopics([
        new NewTopic(STREAM_PENDING, 1, (short) 1),
        new NewTopic(STREAM_PROCESSED, 1, (short) 1),
      ]).all().get(10, TimeUnit.SECONDS)
    }

    producer = new KafkaProducer<>(producerProps(kafka.bootstrapServers))

    def consumerProps = [
      "bootstrap.servers"      : kafka.bootstrapServers,
      "group.id"               : "test",
      "enable.auto.commit"     : "true",
      "auto.commit.interval.ms": "10",
      "session.timeout.ms"     : "30000",
      "key.deserializer"       : IntegerDeserializer,
      "value.deserializer"     : StringDeserializer
    ]
    consumer = new KafkaConsumer<>(consumerProps)

    consumer.subscribe([STREAM_PROCESSED], new ConsumerRebalanceListener() {
      @Override
      void onPartitionsRevoked(Collection<TopicPartition> collection) {
      }

      @Override
      void onPartitionsAssigned(Collection<TopicPartition> collection) {
        consumerReady.countDown()
      }
    })
  }

  def cleanupSpec() {
    consumer?.close()
    producer?.close()
    kafka.stop()
  }

  def "test kafka produce and consume with streams in-between"() {
    setup:
    def config = new Properties()
    config.putAll(producerProps(kafka.bootstrapServers))
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName())
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())

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

    when:
    String greeting = "TESTING TESTING 123!"
    producer.send(new ProducerRecord<>(STREAM_PENDING, greeting))

    then:
    awaitUntilConsumerIsReady()
    def records = consumer.poll(Duration.ofSeconds(10).toMillis())
    Headers receivedHeaders = null
    for (record in records) {
      Span.current().setAttribute("testing", 123)

      assert record.value() == greeting.toLowerCase()
      assert record.key() == null

      if (receivedHeaders == null) {
        receivedHeaders = record.headers()
      }
    }

    assertTraces(3) {
      traces.sort(orderByRootSpanName(
        STREAM_PENDING + " send",
        STREAM_PENDING + " receive",
        STREAM_PROCESSED + " receive"))

      SpanData producerPending, producerProcessed

      trace(0, 1) {
        // kafka-clients PRODUCER
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

        producerPending = span(0)
      }
      trace(1, 3) {
        // kafka-clients CONSUMER receive
        span(0) {
          name STREAM_PENDING + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
          }
        }
        // kafka-stream CONSUMER
        span(1) {
          name STREAM_PENDING + " process"
          kind CONSUMER
          childOf span(0)
          hasLink(producerPending)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
            "asdf" "testing"
          }
        }
        // kafka-clients PRODUCER
        span(2) {
          name STREAM_PROCESSED + " send"
          kind PRODUCER
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }

        producerProcessed = span(2)
      }
      trace(2, 2) {
        // kafka-clients CONSUMER receive
        span(0) {
          name STREAM_PROCESSED + " receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
          }
        }
        // kafka-clients CONSUMER process
        span(1) {
          name STREAM_PROCESSED + " process"
          kind CONSUMER
          childOf span(0)
          hasLink producerProcessed
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

    receivedHeaders.iterator().hasNext()
    def traceparent = new String(receivedHeaders.headers("traceparent").iterator().next().value())
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
    def streamTrace = traces.find { it.size() == 3 }
    def streamSendSpan = streamTrace[2]
    spanContext.traceId == streamSendSpan.traceId
    spanContext.spanId == streamSendSpan.spanId
  }

  private static Map<String, Object> producerProps(String servers) {
    // values copied from spring's KafkaTestUtils
    return [
      "bootstrap.servers": servers,
      "retries"          : 0,
      "batch.size"       : "16384",
      "linger.ms"        : 1,
      "buffer.memory"    : "33554432",
      "key.serializer"   : IntegerSerializer,
      "value.serializer" : StringSerializer
    ]
  }

  // Kafka's eventual consistency behavior forces us to do a couple of empty poll() calls until it gets properly assigned a topic partition
  static void awaitUntilConsumerIsReady() {
    if (consumerReady.await(0, TimeUnit.SECONDS)) {
      return
    }
    for (i in 0..<10) {
      consumer.poll(0)
      if (consumerReady.await(1, TimeUnit.SECONDS)) {
        break
      }
    }
    if (consumerReady.getCount() != 0) {
      throw new AssertionError("Consumer wasn't assigned any partitions!")
    }
    consumer.seekToBeginning([])
  }
}
