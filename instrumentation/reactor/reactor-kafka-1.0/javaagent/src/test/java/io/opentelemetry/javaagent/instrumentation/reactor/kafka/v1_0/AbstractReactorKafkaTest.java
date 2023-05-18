/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singleton;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

abstract class AbstractReactorKafkaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractReactorKafkaTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static KafkaContainer kafka;
  static KafkaSender<String, String> sender;
  static KafkaReceiver<String, String> receiver;

  @BeforeAll
  static void setUpAll() {
    kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.10"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();

    sender = KafkaSender.create(SenderOptions.create(producerProps()));
    receiver =
        KafkaReceiver.create(
            ReceiverOptions.<String, String>create(consumerProps())
                .subscription(singleton("testTopic")));
  }

  @AfterAll
  static void tearDownAll() {
    if (sender != null) {
      sender.close();
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

  protected static List<AttributeAssertion> sendAttributes(ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("producer")),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    return assertions;
  }

  protected static List<AttributeAssertion> receiveAttributes(String topic) {
    return new ArrayList<>(
        Arrays.asList(
            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
            equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, topic),
            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
            satisfies(
                SemanticAttributes.MESSAGING_KAFKA_CLIENT_ID,
                stringAssert -> stringAssert.startsWith("consumer"))));
  }

  protected static List<AttributeAssertion> processAttributes(
      ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("consumer")),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_SOURCE_PARTITION,
                    AbstractLongAssert::isNotNegative),
                satisfies(
                    SemanticAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));
    if (Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes")) {
      assertions.add(
          satisfies(
              AttributeKey.longKey("kafka.record.queue_time_ms"),
              AbstractLongAssert::isNotNegative));
    }
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    String messageValue = record.value();
    if (messageValue != null) {
      assertions.add(
          equalTo(
              SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
              messageValue.getBytes(StandardCharsets.UTF_8).length));
    }
    return assertions;
  }
}
