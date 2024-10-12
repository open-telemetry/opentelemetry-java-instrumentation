/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import groovy.lang.MissingMethodException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.TopologyBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaStreamsSuppressReceiveSpansTest extends KafkaStreamsBaseTest {

  @SuppressWarnings("ClassNewInstance")
  private static @NotNull Object createBuilder()
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    Object builder;
    try {
      // Different class names for test and latestDepTest.
      builder = Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder").newInstance();
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      builder = Class.forName("org.apache.kafka.streams.StreamsBuilder").newInstance();
    }
    return builder;
  }

  @SuppressWarnings("unchecked")
  private static KStream<Integer, String> stream(Object builder)
      throws IllegalAccessException,
          InvocationTargetException,
          NoSuchMethodException,
          ClassNotFoundException {
    Method streamMethod;
    try {
      streamMethod =
          Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder")
              .getMethod("stream", String[].class);
    } catch (ClassNotFoundException e) {
      streamMethod =
          Class.forName("org.apache.kafka.streams.StreamsBuilder")
              .getMethod("stream", String.class);
    }
    return (KStream<Integer, String>) streamMethod.invoke(builder, STREAM_PENDING);
  }

  @DisplayName("test kafka produce and consume with streams in-between")
  @Test
  void testKafkaProduceAndConsumeWithStreamsInBetween()
      throws ExecutionException,
          InterruptedException,
          TimeoutException,
          InstantiationException,
          IllegalAccessException,
          ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException {
    Properties config = new Properties();
    config.putAll(producerProps(KafkaStreamsBaseTest.kafka.getBootstrapServers()));
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application");
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName());
    config.put(
        StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());

    // CONFIGURE PROCESSOR
    Object builder = createBuilder();
    KStream<Integer, String> textLines = stream(builder);
    KStream<Integer, String> values =
        textLines.mapValues(
            textLine -> {
              Span.current().setAttribute("asdf", "testing");
              return textLine.toLowerCase(Locale.ROOT);
            });

    KafkaStreams streams = createStreams(builder, values, config);
    streams.start();

    String greeting = "TESTING TESTING 123!";
    KafkaStreamsBaseTest.producer.send(new ProducerRecord<>(STREAM_PENDING, 10, greeting));

    // check that the message was received
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

    AtomicReference<SpanData> streamSendSpanRef = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                // kafka-clients PRODUCER
                span ->
                    span.hasName(STREAM_PENDING + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                STREAM_PENDING),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_CLIENT_ID, "producer-1"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                                k -> k.isInstanceOf(String.class)),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                            equalTo(
                                MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10")),
                // kafka-stream CONSUMER
                span -> {
                  List<AttributeAssertion> assertions =
                      asList(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              STREAM_PENDING),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                              k -> k.endsWith("consumer")),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                              k -> k.isInstanceOf(Long.class)),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                              k -> k.isInstanceOf(String.class)),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              longKey("kafka.record.queue_time_ms"),
                              k -> k.isGreaterThanOrEqualTo(0)),
                          equalTo(stringKey("asdf"), "testing"));
                  if (Boolean.getBoolean("testLatestDeps")) {
                    assertions.add(
                        equalTo(
                            MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                            "test-application"));
                  }
                  ;
                  span.hasName(STREAM_PENDING + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(assertions);
                },
                // kafka-clients PRODUCER
                span -> {
                  streamSendSpanRef.set(trace.getSpan(2));
                  span.hasName(STREAM_PROCESSED + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(1))
                      .hasAttributesSatisfyingExactly(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              STREAM_PROCESSED),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                              k -> k.isInstanceOf(String.class)),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                              k -> k.isInstanceOf(String.class)),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, 0));
                },
                // kafka-clients CONSUMER process
                span -> {
                  List<AttributeAssertion> assertions =
                      asList(
                          equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                              STREAM_PROCESSED),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                              k -> k.startsWith("consumer")),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                              k -> k.isInstanceOf(Long.class)),
                          satisfies(
                              MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                              k -> k.isInstanceOf(String.class)),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET, 0),
                          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, "10"),
                          satisfies(
                              longKey("kafka.record.queue_time_ms"),
                              k -> k.isGreaterThanOrEqualTo(0)),
                          equalTo(longKey("testing"), 123));
                  if (Boolean.getBoolean("testLatestDeps")) {
                    assertions.add(
                        equalTo(
                            MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP,
                            "test-application"));
                  }
                  span.hasName(STREAM_PENDING + " process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(2))
                      .hasAttributesSatisfyingExactly(assertions);
                }));

    assertThat(receivedHeaders.iterator().hasNext()).isTrue();
    String traceparent =
        new String(
            receivedHeaders.headers("traceparent").iterator().next().value(),
            StandardCharsets.UTF_8);
    Context context =
        W3CTraceContextPropagator.getInstance()
            .extract(
                Context.root(),
                "",
                new TextMapGetter<String>() {
                  @Override
                  public String get(String carrier, String key) {
                    if ("traceparent".equals(key)) {
                      return traceparent;
                    }
                    return null;
                  }

                  @Override
                  public Iterable<String> keys(String carrier) {
                    return Collections.singleton("traceparent");
                  }
                });
    SpanContext spanContext = Span.fromContext(context).getSpanContext();
    SpanData streamSendSpan = streamSendSpanRef.get();
    assertThat(spanContext.getTraceId()).isEqualTo(streamSendSpan.getTraceId());
    assertThat(spanContext.getSpanId()).isEqualTo(streamSendSpan.getSpanId());
  }

  private static KafkaStreams createStreams(
      Object builder, KStream<Integer, String> values, Properties config)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException,
          InstantiationException {
    try {
      // Different api for test and latestDepTest.
      values.to(Serdes.Integer(), Serdes.String(), STREAM_PROCESSED);
      return new KafkaStreams((TopologyBuilder) builder, config);
    } catch (MissingMethodException e) {
      // equivalent to:
      //    Produced<Integer, String> produced = Produced.with(Serdes.Integer(), Serdes.String());
      //    values.to(STREAM_PROCESSED, produced);
      //
      //    Topology topology = builder.build();
      //    new KafkaStreams(topology, props);
      Class<?> producedClass = Class.forName("org.apache.kafka.streams.kstream.Produced");
      Method producedWith = producedClass.getMethod("with", Serde.class, Serde.class);
      Object producer = producedWith.invoke(null, Serdes.Integer(), Serdes.String());

      Class<?> ksteamClass = Class.forName("org.apache.kafka.streams.kstream.KStream");
      ksteamClass
          .getMethod("to", String.class, producedClass)
          .invoke(values, STREAM_PROCESSED, producer);

      Class<?> streamsBuilderClass = Class.forName("org.apache.kafka.streams.StreamsBuilder");
      Object topology = streamsBuilderClass.getMethod("build").invoke(builder);

      Class<?> ksteamsClass = Class.forName("org.apache.kafka.streams.KStreams");
      Class<?> topologyClass = Class.forName("org.apache.kafka.streams.Topology");
      Constructor<?> constructor = ksteamsClass.getConstructor(topologyClass, Properties.class);
      return (KafkaStreams) constructor.newInstance(topology, config);
    }
  }
}
