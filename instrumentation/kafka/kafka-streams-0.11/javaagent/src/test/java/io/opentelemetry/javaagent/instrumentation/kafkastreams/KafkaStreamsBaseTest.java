/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

class KafkaStreamsBaseTest {
  private static final Logger logger =
      LoggerFactory.getLogger("io.opentelemetry.KafkaStreamsBaseTest");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

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
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.Kafka.*Server\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();
    cleanup.deferCleanup(kafka::stop);

    // create test topic
    AdminClient adminClient =
        AdminClient.create(ImmutableMap.of("bootstrap.servers", kafka.getBootstrapServers()));
    cleanup.deferCleanup(adminClient);
    adminClient
        .createTopics(
            asList(
                new NewTopic(STREAM_PENDING, 1, (short) 1),
                new NewTopic(STREAM_PROCESSED, 1, (short) 1)))
        .all()
        .get(10, TimeUnit.SECONDS);

    producer = new KafkaProducer<>(producerProps(kafka.getBootstrapServers()));
    cleanup.deferCleanup(producer);

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
    cleanup.deferCleanup(consumer);
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
}
