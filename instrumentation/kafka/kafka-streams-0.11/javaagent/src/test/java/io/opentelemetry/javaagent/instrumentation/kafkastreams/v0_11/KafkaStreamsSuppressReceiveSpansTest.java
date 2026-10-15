/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CLUSTER_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.StringAssertConsumer;
import io.opentelemetry.sdk.trace.data.LinkData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
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

class KafkaStreamsSuppressReceiveSpansTest extends KafkaStreamsBaseTest {

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
    // check that the message was received
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                // kafka-clients PRODUCER
                span -> {
                  List<AttributeAssertion> pendingProducerAttrs =
                      new ArrayList<>(
                          producerAttributes(
                              STREAM_PENDING, val -> val.isEqualTo("producer-1"), true));
                  pendingProducerAttrs.add(
                      satisfies(MESSAGING_KAFKA_CLUSTER_ID, val -> val.isNotEmpty()));
                  span.hasName("send " + STREAM_PENDING)
                      .hasKind(SpanKind.PRODUCER)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(pendingProducerAttrs);
                },
                // kafka-stream CONSUMER
                span ->
                    span.hasName("process " + STREAM_PENDING)
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(asRemote(trace.getSpan(0).getSpanContext())))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(
                                STREAM_PENDING,
                                "test-application",
                                val -> val.endsWith("consumer"),
                                equalTo(stringKey("asdf"), "testing"))),
                // kafka-clients PRODUCER
                span -> {
                  List<AttributeAssertion> processedProducerAttrs =
                      new ArrayList<>(
                          producerAttributes(
                              STREAM_PROCESSED, val -> val.isInstanceOf(String.class), false));
                  // cluster.id: best-effort; Streams internal producer may lack it on first send.
                  if (trace.getSpan(2).getAttributes().get(MESSAGING_KAFKA_CLUSTER_ID) != null) {
                    processedProducerAttrs.add(
                        satisfies(MESSAGING_KAFKA_CLUSTER_ID, val -> val.isNotEmpty()));
                  }
                  span.hasName("send " + STREAM_PROCESSED)
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(1))
                      .hasTraceId(receivedContext.getTraceId())
                      .hasSpanId(receivedContext.getSpanId())
                      .hasAttributesSatisfyingExactly(processedProducerAttrs);
                },
                // kafka-clients CONSUMER process
                span ->
                    span.hasName("process " + STREAM_PROCESSED)
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(2))
                        .hasLinks(LinkData.create(asRemote(trace.getSpan(2).getSpanContext())))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(
                                STREAM_PROCESSED,
                                "test",
                                val -> val.startsWith("consumer"),
                                equalTo(longKey("testing"), 123)))));
    assertProcessMetricsWithConsumedMessages(
        testing,
        "io.opentelemetry.kafka-streams-0.11",
        STREAM_PENDING,
        testLatestDeps() ? "test-application" : null,
        "0",
        1,
        1,
        null);
  }

  private static List<AttributeAssertion> producerAttributes(
      String topic, StringAssertConsumer clientIdAssertion, boolean includeKey) {
    List<AttributeAssertion> assertions = commonAttributes(topic, "send", "send");
    assertions.add(satisfies(MESSAGING_CLIENT_ID, clientIdAssertion));
    assertions.add(
        satisfies(MESSAGING_DESTINATION_PARTITION_ID, val -> val.isInstanceOf(String.class)));
    assertions.add(equalTo(MESSAGING_KAFKA_OFFSET, 0));
    if (includeKey) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"));
    }
    assertions.add(
        equalTo(
            stringKey("messaging.kafka.bootstrap.servers"),
            EXPERIMENTAL_ATTRIBUTES ? kafka.getBootstrapServers() : null));
    return assertions;
  }

  private static List<AttributeAssertion> processAttributes(
      String topic,
      String consumerGroup,
      StringAssertConsumer clientIdAssertion,
      AttributeAssertion extra) {
    List<AttributeAssertion> assertions = commonAttributes(topic, "process", "process");
    assertions.add(satisfies(MESSAGING_CLIENT_ID, clientIdAssertion));
    assertions.add(
        satisfies(MESSAGING_DESTINATION_PARTITION_ID, val -> val.isInstanceOf(String.class)));
    assertions.add(equalTo(MESSAGING_KAFKA_OFFSET, 0));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, "10"));
    assertions.add(extra);
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, val -> val.isNotEmpty()));
    if (EXPERIMENTAL_ATTRIBUTES) {
      assertions.add(
          satisfies(longKey("kafka.record.queue_time_ms"), val -> val.isGreaterThanOrEqualTo(0)));
    }
    if (testLatestDeps()) {
      assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup));
    }
    return assertions;
  }

  private static List<AttributeAssertion> commonAttributes(
      String topic, String operationName, String operationType) {
    return new ArrayList<>(
        asList(
            equalTo(MESSAGING_SYSTEM, KAFKA),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_OPERATION_NAME, operationName),
            equalTo(MESSAGING_OPERATION_TYPE, operationType)));
  }
}
