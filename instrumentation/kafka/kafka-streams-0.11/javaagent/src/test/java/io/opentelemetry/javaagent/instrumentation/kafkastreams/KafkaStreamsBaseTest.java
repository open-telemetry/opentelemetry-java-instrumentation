/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

abstract class KafkaStreamsBaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected static final AttributeKey<String> MESSAGING_CLIENT_ID =
      AttributeKey.stringKey("messaging.client_id");
  protected static final String STREAM_PENDING = "test.pending";
  protected static final String STREAM_PROCESSED = "test.processed";

  static KafkaContainer kafka;
  static Producer<Integer, String> producer;
  static Consumer<Integer, String> consumer;
  static CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  static void setup() throws ExecutionException, InterruptedException, TimeoutException {
    kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.Kafka.*Server\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();

    // create test topic
    try (AdminClient adminClient =
        AdminClient.create(ImmutableMap.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      adminClient
          .createTopics(
              asList(
                  new NewTopic(STREAM_PENDING, 1, (short) 1),
                  new NewTopic(STREAM_PROCESSED, 1, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }

    producer = new KafkaProducer<>(producerProps(kafka.getBootstrapServers()));

    Map<String, Object> consumerProps =
        ImmutableMap.of(
            "bootstrap.servers",
            kafka.getBootstrapServers(),
            "group.id",
            "test",
            "enable.auto.commit",
            "true",
            "auto.commit.interval.ms",
            "10",
            "session.timeout.ms",
            "30000",
            "key.deserializer",
            IntegerDeserializer.class,
            "value.deserializer",
            StringDeserializer.class);
    consumer = new KafkaConsumer<>(consumerProps);
    consumer.subscribe(
        singleton(STREAM_PROCESSED),
        new ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(Collection<TopicPartition> collection) {}

          @Override
          public void onPartitionsAssigned(Collection<TopicPartition> collection) {
            consumerReady.countDown();
          }
        });
  }

  @AfterAll
  static void cleanup() {
    consumer.close();
    producer.close();
    kafka.stop();
  }

  static Map<String, Object> producerProps(String servers) {
    // values copied from spring's KafkaTestUtils
    return ImmutableMap.of(
        "bootstrap.servers",
        servers,
        "retries",
        0,
        "batch.size",
        "16384",
        "linger.ms",
        1,
        "buffer.memory",
        "33554432",
        "key.serializer",
        IntegerSerializer.class,
        "value.serializer",
        StringSerializer.class);
  }

  // Kafka's eventual consistency behavior forces us to do a couple of empty poll() calls until it
  // gets properly assigned a topic partition
  @SuppressWarnings("PreferJavaTimeOverload")
  static void awaitUntilConsumerIsReady() throws InterruptedException {
    if (consumerReady.await(0, TimeUnit.SECONDS)) {
      return;
    }
    for (int i = 0; i < 10; i++) {
      consumer.poll(0);
      if (consumerReady.await(1, TimeUnit.SECONDS)) {
        break;
      }
    }
    if (consumerReady.getCount() != 0) {
      throw new AssertionError("Consumer wasn't assigned any partitions!");
    }
    consumer.seekToBeginning(Collections.emptyList());
  }

  static Context getContext(Headers headers) {
    String traceparent =
        new String(
            headers.headers("traceparent").iterator().next().value(), StandardCharsets.UTF_8);
    return W3CTraceContextPropagator.getInstance()
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
  }
}
