/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
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

@SuppressWarnings("deprecation") // using deprecated semconv
class KafkaStreamsDefaultTest extends KafkaStreamsBaseTest {

  @DisplayName("test kafka produce and consume with streams in-between")
  @Test
  void testKafkaProduceAndConsumeWithStreamsInBetween() throws Exception {
    Properties config = new Properties();
    config.putAll(producerProps(kafka.getBootstrapServers()));
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
    cleanup.deferCleanup(() -> streams.close());
    streams.start();

    String greeting = "TESTING TESTING 123!";
    producer.send(new ProducerRecord<>(STREAM_PENDING, 10, greeting));

    awaitUntilConsumerIsReady();
    @SuppressWarnings("PreferJavaTimeOverload")
    ConsumerRecords<Integer, String> records = poll(Duration.ofSeconds(10));
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
            emitStableMessagingSemconv() ? "send " + STREAM_PENDING : STREAM_PENDING + " publish",
            emitStableMessagingSemconv() ? "poll " + STREAM_PENDING : STREAM_PENDING + " receive",
            emitStableMessagingSemconv()
                ? "poll " + STREAM_PROCESSED
                : STREAM_PROCESSED + " receive"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              // kafka-clients PRODUCER
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + STREAM_PENDING
                              : STREAM_PENDING + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(producerAttributes(STREAM_PENDING, true)));
          producerPendingRef.set(trace.getSpan(0));
        },
        trace -> {
          trace.hasSpansSatisfyingExactly(
              // kafka-clients CONSUMER receive
              span -> {
                List<AttributeAssertion> assertions =
                    new ArrayList<>(
                        messagingAttributes(
                            STREAM_PENDING, "receive", "poll", "receive", "consumer", false));
                assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
                if (testLatestDeps()) {
                  addGroupAssertions(assertions, "test-application");
                }
                span.hasName(
                        emitStableMessagingSemconv()
                            ? "poll " + STREAM_PENDING
                            : STREAM_PENDING + " receive")
                    .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(assertions);
              },
              // kafka-stream CONSUMER
              span -> {
                List<AttributeAssertion> assertions =
                    new ArrayList<>(
                        messagingAttributes(
                            STREAM_PENDING, "process", "process", "process", "consumer", false));
                assertions.add(
                    satisfies(MESSAGING_MESSAGE_BODY_SIZE, val -> val.isInstanceOf(Long.class)));
                assertions.add(
                    satisfies(
                        MESSAGING_DESTINATION_PARTITION_ID, val -> val.isInstanceOf(String.class)));
                assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"));
                assertions.add(equalTo(stringKey("asdf"), "testing"));
                addOffsetAssertions(assertions, 0);

                if (EXPERIMENTAL_ATTRIBUTES) {
                  assertions.add(
                      satisfies(
                          longKey("kafka.record.queue_time_ms"),
                          val -> val.isGreaterThanOrEqualTo(0)));
                }

                if (testLatestDeps()) {
                  addGroupAssertions(assertions, "test-application");
                }
                span.hasName(
                        emitStableMessagingSemconv()
                            ? "process " + STREAM_PENDING
                            : STREAM_PENDING + " process")
                    .hasKind(SpanKind.CONSUMER)
                    .hasParent(trace.getSpan(0))
                    .hasLinks(LinkData.create(producerPendingRef.get().getSpanContext()))
                    .hasAttributesSatisfyingExactly(assertions);
              },
              // kafka-clients PRODUCER
              span ->
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "send " + STREAM_PROCESSED
                              : STREAM_PROCESSED + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(1))
                      .hasTraceId(receivedContext.getTraceId())
                      .hasSpanId(receivedContext.getSpanId())
                      .hasAttributesSatisfyingExactly(producerAttributes(STREAM_PROCESSED, false)));

          producerProcessedRef.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                // kafka-clients CONSUMER receive
                span -> {
                  List<AttributeAssertion> assertions =
                      new ArrayList<>(
                          messagingAttributes(
                              STREAM_PROCESSED, "receive", "poll", "receive", "consumer", true));
                  assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
                  if (testLatestDeps()) {
                    addGroupAssertions(assertions, "test");
                  }
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "poll " + STREAM_PROCESSED
                              : STREAM_PROCESSED + " receive")
                      .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(assertions);
                },
                // kafka-clients CONSUMER process
                span -> {
                  List<AttributeAssertion> assertions =
                      new ArrayList<>(
                          messagingAttributes(
                              STREAM_PROCESSED, "process", "process", "process", "consumer", true));
                  assertions.add(
                      satisfies(MESSAGING_MESSAGE_BODY_SIZE, val -> val.isInstanceOf(Long.class)));
                  assertions.add(
                      satisfies(
                          MESSAGING_DESTINATION_PARTITION_ID,
                          val -> val.isInstanceOf(String.class)));
                  assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"));
                  assertions.add(equalTo(longKey("testing"), 123));
                  addOffsetAssertions(assertions, 0);
                  if (EXPERIMENTAL_ATTRIBUTES) {
                    assertions.add(
                        satisfies(
                            longKey("kafka.record.queue_time_ms"),
                            val -> val.isGreaterThanOrEqualTo(0)));
                  }

                  if (testLatestDeps()) {
                    addGroupAssertions(assertions, "test");
                  }
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process " + STREAM_PROCESSED
                              : STREAM_PROCESSED + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
                      .hasLinks(LinkData.create(producerProcessedRef.get().getSpanContext()))
                      .hasAttributesSatisfyingExactly(assertions);
                }));
  }

  private static List<AttributeAssertion> producerAttributes(String topic, boolean includeKey) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            messagingAttributes(topic, "publish", "send", "send", "producer", includeKey));
    assertions.add(
        satisfies(MESSAGING_DESTINATION_PARTITION_ID, val -> val.isInstanceOf(String.class)));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, includeKey ? "10" : null));
    assertions.add(
        equalTo(
            stringKey("messaging.kafka.bootstrap.servers"),
            EXPERIMENTAL_ATTRIBUTES ? kafka.getBootstrapServers() : null));
    addOffsetAssertions(assertions, 0);
    return assertions;
  }

  private static List<AttributeAssertion> messagingAttributes(
      String topic,
      String oldOperation,
      String operationName,
      String operationType,
      String clientIdSuffix,
      boolean startsWith) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, KAFKA),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? oldOperation : null),
                equalTo(
                    MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operationName : null),
                equalTo(
                    MESSAGING_OPERATION_TYPE,
                    emitStableMessagingSemconv() ? operationType : null)));
    if (emitOldMessagingSemconv()) {
      assertions.add(
          satisfies(
              stringKey("messaging.client_id"),
              val -> {
                if (startsWith) {
                  val.startsWith(clientIdSuffix);
                } else {
                  val.endsWith(clientIdSuffix);
                }
              }));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(
          satisfies(
              stringKey("messaging.client.id"),
              val -> {
                if (startsWith) {
                  val.startsWith(clientIdSuffix);
                } else {
                  val.endsWith(clientIdSuffix);
                }
              }));
    }
    return assertions;
  }

  private static void addOffsetAssertions(List<AttributeAssertion> assertions, long offset) {
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_OFFSET, offset));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_KAFKA_OFFSET, offset));
    }
  }

  private static void addGroupAssertions(List<AttributeAssertion> assertions, String group) {
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, group));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    }
  }
}
