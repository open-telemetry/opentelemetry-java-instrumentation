/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

public abstract class AbstractReactorKafkaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractReactorKafkaTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static KafkaContainer kafka;
  protected static KafkaSender<String, String> sender;
  protected static KafkaReceiver<String, String> receiver;

  @BeforeAll
  static void setUpAll() {
    kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.10"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    kafka.start();

    sender = KafkaSender.create(senderOptions());
    receiver = KafkaReceiver.create(receiverOptions());
  }

  @AfterAll
  static void tearDownAll() {
    if (sender != null) {
      sender.close();
    }
    kafka.stop();
  }

  @SuppressWarnings("unchecked")
  private static SenderOptions<String, String> senderOptions() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("retries", 0);
    props.put("key.serializer", StringSerializer.class);
    props.put("value.serializer", StringSerializer.class);

    try {
      // SenderOptions changed from a class to an interface in 1.3.3, using reflection to avoid
      // linkage error
      return (SenderOptions<String, String>)
          SenderOptions.class.getMethod("create", Properties.class).invoke(null, props);
    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static ReceiverOptions<String, String> receiverOptions() {
    Properties props = new Properties();
    props.put("bootstrap.servers", kafka.getBootstrapServers());
    props.put("group.id", "test");
    props.put("enable.auto.commit", true);
    props.put("auto.commit.interval.ms", 10);
    props.put("session.timeout.ms", 30000);
    props.put("auto.offset.reset", "earliest");
    props.put("key.deserializer", StringDeserializer.class);
    props.put("value.deserializer", StringDeserializer.class);

    try {
      // SenderOptions changed from a class to an interface in 1.3.3, using reflection to avoid
      // linkage error
      ReceiverOptions<String, String> receiverOptions =
          (ReceiverOptions<String, String>)
              ReceiverOptions.class.getMethod("create", Properties.class).invoke(null, props);
      return (ReceiverOptions<String, String>)
          ReceiverOptions.class
              .getMethod("subscription", Collection.class)
              .invoke(receiverOptions, singleton("testTopic"));
    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  protected void testSingleRecordProcess(
      Function<Consumer<ConsumerRecord<String, String>>, Disposable> subscriptionFunction) {
    Disposable disposable =
        subscriptionFunction.apply(record -> testing.runWithSpan("consumer", () -> {}));
    cleanup.deferCleanup(disposable::dispose);

    SenderRecord<String, String, Object> record =
        SenderRecord.create("testTopic", 0, null, "10", "testSpan", null);
    Flux<?> producer = sender.send(Flux.just(record));
    testing.runWithSpan("producer", () -> producer.blockLast(Duration.ofSeconds(30)));

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("testTopic publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes(record)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes("testTopic")),
                span ->
                    span.hasName("testTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  protected static List<AttributeAssertion> sendAttributes(ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "publish"),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("producer")),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                    AbstractStringAssert::isNotEmpty),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(
          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    return assertions;
  }

  protected static List<AttributeAssertion> receiveAttributes(String topic) {
    ArrayList<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topic),
                equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "receive"),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("consumer")),
                equalTo(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, 1)));
    if (Boolean.getBoolean("hasConsumerGroup")) {
      assertions.add(equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
    }
    return assertions;
  }

  protected static List<AttributeAssertion> processAttributes(
      ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka"),
                equalTo(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic()),
                equalTo(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process"),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_CLIENT_ID,
                    stringAssert -> stringAssert.startsWith("consumer")),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID,
                    AbstractStringAssert::isNotEmpty),
                satisfies(
                    MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET,
                    AbstractLongAssert::isNotNegative)));
    if (Boolean.getBoolean("hasConsumerGroup")) {
      assertions.add(equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
    }
    if (Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes")) {
      assertions.add(
          satisfies(longKey("kafka.record.queue_time_ms"), AbstractLongAssert::isNotNegative));
    }
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(
          equalTo(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    String messageValue = record.value();
    if (messageValue != null) {
      assertions.add(
          equalTo(
              MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
              messageValue.getBytes(StandardCharsets.UTF_8).length));
    }
    return assertions;
  }
}
