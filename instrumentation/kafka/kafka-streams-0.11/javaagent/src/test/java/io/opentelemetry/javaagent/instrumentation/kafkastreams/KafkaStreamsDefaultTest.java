/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaStreamsDefaultTest extends KafkaStreamsBaseTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @DisplayName("test kafka produce and consume with streams in-between")
  @Test
  void testKafkaProduceAndConsumeWithStreamsInBetween() throws Exception {
    Properties config = new Properties();
    config.putAll(producerProps(KafkaStreamsBaseTest.kafka.getBootstrapServers()));
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

    // CONFIGURE PROCESSOR
    KafkaStreamsReflectionUtil.StreamBuilder streamBuilder =
        KafkaStreamsReflectionUtil.createBuilder();
    KStream<Integer, String> textLines = streamBuilder.stream(STREAM_PENDING);
    KStream<Integer, String> values =
        textLines.mapValues(
            textLine -> {
              Span.current().setAttribute("asdf", "testing");
              return textLine.toLowerCase(Locale.ROOT);
            });

    KafkaStreams streams = streamBuilder.createStreams(values, config, STREAM_PROCESSED);
    streams.start();

    String greeting = "TESTING TESTING 123!";
    KafkaStreamsBaseTest.producer.send(new ProducerRecord<>(STREAM_PENDING, 10, greeting));

    awaitUntilConsumerIsReady();
    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<Integer, String> records =
        KafkaStreamsBaseTest.consumer.poll(Duration.ofSeconds(10).toMillis());
    Headers receivedHeaders = null;
    for (ConsumerRecord<Integer, String> record : records) {
      Span.current().setAttribute("testing", 123);

      assertThat(record.key()).isEqualTo(10);
      assertThat(record.value()).isEqualTo(greeting.toLowerCase(Locale.ROOT));

      if (receivedHeaders == null) {
        receivedHeaders = record.headers();
      }
    }
    assertThat(receivedHeaders).isNotEmpty();
    SpanContext receivedContext = Span.fromContext(getContext(receivedHeaders)).getSpanContext();

    AtomicReference<SpanData> producerPendingRef = new AtomicReference<>();
    AtomicReference<SpanData> producerProcessedRef = new AtomicReference<>();

    // Add your assertTraces logic here
    testing.waitAndAssertSortedTraces(
        TelemetryDataUtil.orderByRootSpanName(
            STREAM_PENDING + " publish",
            STREAM_PENDING + " receive",
            STREAM_PROCESSED + " receive"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              // kafka-clients PRODUCER
              span ->
                  span.hasName(STREAM_PENDING + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, KAFKA),
                          equalTo(MESSAGING_DESTINATION_NAME, STREAM_PENDING),
                          equalTo(MESSAGING_OPERATION, "publish"),
                          satisfies(MESSAGING_CLIENT_ID, k -> k.startsWith("producer")),
                          satisfies(
                              MESSAGING_DESTINATION_PARTITION_ID,
                              k -> k.isInstanceOf(String.class)),
                          equalTo(MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                          equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10")));
          producerPendingRef.set(trace.getSpan(0));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              // kafka-clients CONSUMER receive
              span -> {
                List<AttributeAssertion> assertions =
                    new ArrayList<>(
                        asList(
                            equalTo(MESSAGING_SYSTEM, KAFKA),
                            equalTo(MESSAGING_DESTINATION_NAME, STREAM_PENDING),
                            equalTo(MESSAGING_OPERATION, "receive"),
                            satisfies(MESSAGING_CLIENT_ID, k -> k.endsWith("consumer")),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));
                if (Boolean.getBoolean("testLatestDeps")) {
                  assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test-application"));
                }
                span.hasName(STREAM_PENDING + " receive")
                    .hasKind(SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(assertions);
              },
              // kafka-stream CONSUMER
              span -> {
                List<AttributeAssertion> assertions =
                    new ArrayList<>(
                        asList(
                            equalTo(MESSAGING_SYSTEM, KAFKA),
                            equalTo(MESSAGING_DESTINATION_NAME, STREAM_PENDING),
                            equalTo(MESSAGING_OPERATION, "process"),
                            satisfies(MESSAGING_CLIENT_ID, k -> k.endsWith("consumer")),
                            satisfies(MESSAGING_MESSAGE_BODY_SIZE, k -> k.isInstanceOf(Long.class)),
                            satisfies(
                                MESSAGING_DESTINATION_PARTITION_ID,
                                k -> k.isInstanceOf(String.class)),
                            equalTo(MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                            equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                            satisfies(
                                longKey("kafka.record.queue_time_ms"),
                                k -> k.isGreaterThanOrEqualTo(0)),
                            equalTo(stringKey("asdf"), "testing")));
                if (Boolean.getBoolean("testLatestDeps")) {
                  assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test-application"));
                }
                span.hasName(STREAM_PENDING + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0))
                    .hasLinks(LinkData.create(producerPendingRef.get().getSpanContext()))
                    .hasAttributesSatisfyingExactly(assertions);
              },
              // kafka-clients PRODUCER
              span ->
                  span.hasName(STREAM_PROCESSED + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(1))
                      .hasTraceId(receivedContext.getTraceId())
                      .hasSpanId(receivedContext.getSpanId())
                      .hasAttributesSatisfyingExactly(
                          equalTo(MESSAGING_SYSTEM, KAFKA),
                          equalTo(MESSAGING_DESTINATION_NAME, STREAM_PROCESSED),
                          equalTo(MESSAGING_OPERATION, "publish"),
                          satisfies(MESSAGING_CLIENT_ID, k -> k.endsWith("producer")),
                          satisfies(
                              MESSAGING_DESTINATION_PARTITION_ID,
                              k -> k.isInstanceOf(String.class)),
                          equalTo(MESSAGING_KAFKA_MESSAGE_OFFSET, 0)));

          producerProcessedRef.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                // kafka-clients CONSUMER receive
                span -> {
                  List<AttributeAssertion> assertions =
                      new ArrayList<>(
                          asList(
                              equalTo(MESSAGING_SYSTEM, KAFKA),
                              equalTo(MESSAGING_DESTINATION_NAME, STREAM_PROCESSED),
                              equalTo(MESSAGING_OPERATION, "receive"),
                              satisfies(MESSAGING_CLIENT_ID, k -> k.startsWith("consumer")),
                              equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));
                  if (Boolean.getBoolean("testLatestDeps")) {
                    assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
                  }
                  span.hasName(STREAM_PROCESSED + " receive")
                      .hasKind(SpanKind.CONSUMER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(assertions);
                },
                // kafka-clients CONSUMER process
                span -> {
                  List<AttributeAssertion> assertions =
                      new ArrayList<>(
                          asList(
                              equalTo(MESSAGING_SYSTEM, KAFKA),
                              equalTo(MESSAGING_DESTINATION_NAME, STREAM_PROCESSED),
                              equalTo(MESSAGING_OPERATION, "process"),
                              satisfies(MESSAGING_CLIENT_ID, k -> k.startsWith("consumer")),
                              satisfies(
                                  MESSAGING_MESSAGE_BODY_SIZE, k -> k.isInstanceOf(Long.class)),
                              satisfies(
                                  MESSAGING_DESTINATION_PARTITION_ID,
                                  k -> k.isInstanceOf(String.class)),
                              equalTo(MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                              equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                              satisfies(
                                  longKey("kafka.record.queue_time_ms"),
                                  k -> k.isGreaterThanOrEqualTo(0)),
                              equalTo(longKey("testing"), 123)));
                  if (Boolean.getBoolean("testLatestDeps")) {
                    assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
                  }
                  span.hasName(STREAM_PROCESSED + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
                      .hasLinks(LinkData.create(producerProcessedRef.get().getSpanContext()))
                      .hasAttributesSatisfyingExactly(assertions);
                }));
  }
}
