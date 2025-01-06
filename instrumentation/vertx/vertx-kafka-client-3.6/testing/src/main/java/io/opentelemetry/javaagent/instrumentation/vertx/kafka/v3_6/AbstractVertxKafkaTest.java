/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.producer.RecordMetadata;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractVertxKafkaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractVertxKafkaTest.class);

  private static final AttributeKey<String> MESSAGING_CLIENT_ID =
      AttributeKey.stringKey("messaging.client_id");

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static KafkaContainer kafka;
  static Vertx vertx;
  static KafkaProducer<String, String> kafkaProducer;
  protected static KafkaConsumer<String, String> kafkaConsumer;

  @BeforeAll
  static void setUpAll() {
    kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.Kafka.*Server\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();

    vertx = Vertx.vertx();
    kafkaProducer = KafkaProducer.create(vertx, producerProps());
    kafkaConsumer = KafkaConsumer.create(vertx, consumerProps());
  }

  @AfterAll
  static void tearDownAll() {
    if (kafkaConsumer != null) {
      kafkaConsumer.close(unused -> {});
    }
    if (kafkaProducer != null) {
      kafkaProducer.close(unused -> {});
    }
    if (vertx != null) {
      vertx.close(unused -> {});
    }
    kafka.stop();
  }

  private static Properties producerProps() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("retries", 0);
    props.put("key.serializer", StringSerializer.class);
    props.put("value.serializer", StringSerializer.class);
    return props;
  }

  private static Properties consumerProps() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("group.id", "test");
    props.put("enable.auto.commit", true);
    props.put("auto.commit.interval.ms", 10);
    props.put("session.timeout.ms", 30000);
    props.put("auto.offset.reset", "earliest");
    props.put("key.deserializer", StringDeserializer.class);
    props.put("value.deserializer", StringDeserializer.class);
    return props;
  }

  @SafeVarargs
  protected final void sendBatchMessages(KafkaProducerRecord<String, String>... records)
      throws InterruptedException {
    // This test assumes that messages are sent and received as a batch. Occasionally it happens
    // that the messages are not received as a batch, but one by one. This doesn't match what the
    // assertion expects. To reduce flakiness we retry the test when messages weren't received as
    // a batch.
    int maxAttempts = 5;
    for (int i = 1; i <= maxAttempts; i++) {
      BatchRecordsHandler.reset();
      kafkaConsumer.pause();

      // wait a bit to ensure that the consumer has really paused
      Thread.sleep(1000);

      CountDownLatch sent = new CountDownLatch(records.length);
      testing.runWithSpan(
          "producer",
          () -> {
            for (KafkaProducerRecord<String, String> record : records) {
              sendRecord(record, result -> sent.countDown());
            }
          });
      assertTrue(sent.await(30, TimeUnit.SECONDS));

      kafkaConsumer.resume();
      BatchRecordsHandler.waitForMessages();

      if (BatchRecordsHandler.getLastBatchSize() == records.length) {
        break;
      } else if (i < maxAttempts) {
        testing.waitForTraces(2);
        Thread.sleep(1_000); // sleep a bit to give time for all the spans to arrive
        testing.clearData();
        logger.info("Messages weren't received as batch, retrying");
      }
    }
  }

  private static final MethodHandle SEND_METHOD;

  static {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    MethodHandle sendMethod = null;

    // versions 3.6+
    try {
      sendMethod =
          lookup.findVirtual(
              KafkaProducer.class,
              "write",
              MethodType.methodType(KafkaProducer.class, KafkaProducerRecord.class, Handler.class));
    } catch (NoSuchMethodException | IllegalAccessException ignored) {
      // ignore
    }

    // versions 4+
    if (sendMethod == null) {
      try {
        sendMethod =
            lookup.findVirtual(
                KafkaProducer.class,
                "send",
                MethodType.methodType(
                    KafkaProducer.class, KafkaProducerRecord.class, Handler.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        // ignore
      }
    }

    if (sendMethod == null) {
      throw new AssertionError("Could not find send/write method on KafkaProducer");
    }
    SEND_METHOD = sendMethod;
  }

  protected static void sendRecord(
      KafkaProducerRecord<String, String> record, Handler<AsyncResult<RecordMetadata>> handler) {

    try {
      SEND_METHOD.invoke(kafkaProducer, record, handler);
    } catch (Throwable e) {
      throw new AssertionError("Failed producer send/write invocation", e);
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected static List<AttributeAssertion> sendAttributes(
      KafkaProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, KAFKA),
                equalTo(MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(MESSAGING_OPERATION, "publish"),
                satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("producer")),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative)));
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    return assertions;
  }

  protected static List<AttributeAssertion> receiveAttributes(String topic) {
    return batchConsumerAttributes(topic, "receive");
  }

  protected static List<AttributeAssertion> batchProcessAttributes(String topic) {
    return batchConsumerAttributes(topic, "process");
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> batchConsumerAttributes(String topic, String operation) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, KAFKA),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, operation),
                satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
                satisfies(MESSAGING_BATCH_MESSAGE_COUNT, AbstractLongAssert::isPositive)));
    // consumer group is not available in version 0.11
    if (Boolean.getBoolean("testLatestDeps")) {
      assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
    }
    return assertions;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected static List<AttributeAssertion> processAttributes(
      KafkaProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, KAFKA),
                equalTo(MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(MESSAGING_OPERATION, "process"),
                satisfies(MESSAGING_CLIENT_ID, stringAssert -> stringAssert.startsWith("consumer")),
                satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty),
                satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative)));
    if (Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes")) {
      assertions.add(
          satisfies(
              AttributeKey.longKey("kafka.record.queue_time_ms"),
              AbstractLongAssert::isNotNegative));
    }
    // consumer group is not available in version 0.11
    if (Boolean.getBoolean("testLatestDeps")) {
      assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
    }
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    String messageValue = record.value();
    if (messageValue != null) {
      assertions.add(
          equalTo(
              MESSAGING_MESSAGE_BODY_SIZE, messageValue.getBytes(StandardCharsets.UTF_8).length));
    }
    return assertions;
  }
}
