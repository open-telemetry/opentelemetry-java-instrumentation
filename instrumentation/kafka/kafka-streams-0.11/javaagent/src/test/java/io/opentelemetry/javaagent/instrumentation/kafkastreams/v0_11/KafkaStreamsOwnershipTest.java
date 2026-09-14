/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.jupiter.api.Test;

class KafkaStreamsOwnershipTest extends KafkaStreamsBaseTest {

  @Test
  void nestedRawKafkaProcessingIsNotSuppressed() throws Exception {
    KafkaStreamsReflectionUtil.StreamBuilder streamBuilder =
        KafkaStreamsReflectionUtil.createBuilder();
    KStream<Integer, String> values =
        streamBuilder.stream(STREAM_PENDING)
            .mapValues(
                value -> {
                  ConsumerRecords<Integer, String> nestedRecords =
                      records("nested-topic", value.toLowerCase(Locale.ROOT));
                  for (ConsumerRecord<Integer, String> ignored : nestedRecords) {
                    // Iterating a raw Kafka batch represents application-owned consumer work.
                  }
                  return value;
                });

    KafkaStreams streams =
        streamBuilder.createStreams(values, streamsConfig("ownership-success"), STREAM_PROCESSED);
    cleanup.deferCleanup(streams);
    streams.start();
    testing.clearData();

    producer.send(new ProducerRecord<>(STREAM_PENDING, 10, "VALUE"));

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              SpanData streamsProcess =
                  onlySpan("io.opentelemetry.kafka-streams-0.11", STREAM_PENDING);
              SpanData nestedProcess =
                  onlySpan("io.opentelemetry.kafka-clients-0.11", "nested-topic");
              assertThat(nestedProcess.getTraceId()).isEqualTo(streamsProcess.getTraceId());
              assertThat(nestedProcess.getParentSpanId()).isEqualTo(streamsProcess.getSpanId());
            });
  }

  @Test
  void taskFailureEndsProcessSpanWithError() throws Exception {
    CountDownLatch invoked = new CountDownLatch(1);
    KafkaStreamsReflectionUtil.StreamBuilder streamBuilder =
        KafkaStreamsReflectionUtil.createBuilder();
    KStream<Integer, String> values =
        streamBuilder.stream(STREAM_PENDING)
            .mapValues(
                value -> {
                  invoked.countDown();
                  throw new IllegalStateException("test failure");
                });

    KafkaStreams streams =
        streamBuilder.createStreams(values, streamsConfig("ownership-failure"), STREAM_PROCESSED);
    cleanup.deferCleanup(streams);
    streams.start();
    testing.clearData();

    producer.send(new ProducerRecord<>(STREAM_PENDING, 11, "VALUE"));
    assertThat(invoked.await(30, SECONDS)).isTrue();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        onlySpan("io.opentelemetry.kafka-streams-0.11", STREAM_PENDING).getStatus())
                    .extracting(status -> status.getStatusCode())
                    .isEqualTo(StatusCode.ERROR));
  }

  private static Properties streamsConfig(String applicationId) {
    Properties config = new Properties();
    config.putAll(producerProps(kafka.getBootstrapServers()));
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
    return config;
  }

  private static ConsumerRecords<Integer, String> records(String topic, String value) {
    TopicPartition partition = new TopicPartition(topic, 0);
    ConsumerRecord<Integer, String> record = new ConsumerRecord<>(topic, 0, 0, 1, value);
    return new ConsumerRecords<>(singletonMap(partition, singletonList(record)));
  }

  private static SpanData onlySpan(String instrumentationName, String topic) {
    String spanName = emitStableMessagingSemconv() ? "process " + topic : topic + " process";
    List<SpanData> spans =
        testing.spans().stream()
            .filter(
                span -> span.getInstrumentationScopeInfo().getName().equals(instrumentationName))
            .filter(span -> span.getName().equals(spanName))
            .collect(toList());
    assertThat(spans).hasSize(1);
    return spans.get(0);
  }
}
