/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractVertxKafkaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractVertxKafkaTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static KafkaContainer kafka;
  static Vertx vertx;
  static KafkaProducer<String, String> kafkaProducer;
  protected static KafkaConsumer<String, String> kafkaConsumer;

  @BeforeAll
  static void setUpAll() {
    kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
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
}
