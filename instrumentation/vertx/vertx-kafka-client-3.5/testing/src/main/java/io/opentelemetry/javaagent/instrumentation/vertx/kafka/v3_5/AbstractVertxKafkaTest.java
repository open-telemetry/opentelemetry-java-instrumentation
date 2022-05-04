/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_5;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Vertx;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import java.time.Duration;
import java.util.Properties;
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
  protected static KafkaProducer<String, String> kafkaProducer;
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
}
