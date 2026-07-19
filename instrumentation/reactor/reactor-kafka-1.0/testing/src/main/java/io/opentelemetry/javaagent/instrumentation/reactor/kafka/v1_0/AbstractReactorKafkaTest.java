/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.InvocationTargetException;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractReactorKafkaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractReactorKafkaTest.class);

  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes");
  private static final boolean HAS_CONSUMER_GROUP = Boolean.getBoolean("hasConsumerGroup");

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static KafkaContainer kafka;
  private static KafkaSender<String, String> sender;
  protected static KafkaReceiver<String, String> receiver;

  @BeforeAll
  static void setUpAll() {
    kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx256m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.Kafka.*Server\\).*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    cleanup.deferAfterAll(kafka::stop);
    kafka.start();

    sender = KafkaSender.create(senderOptions());
    cleanup.deferAfterAll(sender::close);
    receiver = KafkaReceiver.create(receiverOptions());
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
      // ReceiverOptions changed from a class to an interface in 1.3.3, using reflection to avoid
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

    if (RECEIVE_TELEMETRY_ENABLED) {
      assertWithReceiveTelemetry(record);
    } else {
      assertWithoutReceiveTelemetry(record);
    }
  }

  private static void assertWithReceiveTelemetry(SenderRecord<String, String, Object> record) {
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertSortedTraces(
          orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
          trace -> {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(record)),
                span ->
                    span.hasName(spanName("testTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2)));

            producerSpan.set(trace.getSpan(1));
          },
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(spanName("testTopic", "receive", "poll"))
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                          .hasAttributesSatisfyingExactly(receiveAttributes("testTopic"))));
      return;
    }

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, receiveKind()),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName(spanName("testTopic", "publish", "send"))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(sendAttributes(record)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("testTopic", "receive", "poll"))
                        .hasKind(receiveKind())
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(receiveAttributes("testTopic")),
                span ->
                    span.hasName(spanName("testTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  private static void assertWithoutReceiveTelemetry(SenderRecord<String, String, Object> record) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer"),
                span ->
                    span.hasName(spanName("testTopic", "publish", "send"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(sendAttributes(record)),
                span ->
                    span.hasName(spanName("testTopic", "process", "process"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(processAttributes(record)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  private static List<AttributeAssertion> sendAttributes(ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        messagingAttributes(record.topic(), "publish", "send", "send", "producer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    addOffsetAssertion(assertions);
    if (EXPERIMENTAL_ATTRIBUTES) {
      assertions.add(
          equalTo(stringKey("messaging.kafka.bootstrap.servers"), kafka.getBootstrapServers()));
    }
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    return assertions;
  }

  private static List<AttributeAssertion> receiveAttributes(String topic) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "receive", "poll", "receive", "consumer");
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
    if (HAS_CONSUMER_GROUP) {
      addGroupAssertions(assertions);
    }
    return assertions;
  }

  private static List<AttributeAssertion> processAttributes(ProducerRecord<String, String> record) {
    List<AttributeAssertion> assertions =
        messagingAttributes(record.topic(), "process", "process", "process", "consumer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    addOffsetAssertion(assertions);
    if (HAS_CONSUMER_GROUP) {
      addGroupAssertions(assertions);
    }
    if (EXPERIMENTAL_ATTRIBUTES) {
      assertions.add(
          satisfies(longKey("kafka.record.queue_time_ms"), AbstractLongAssert::isNotNegative));
    }
    String messageKey = record.key();
    if (messageKey != null) {
      assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    }
    String messageValue = record.value();
    if (messageValue != null) {
      assertions.add(equalTo(MESSAGING_MESSAGE_BODY_SIZE, messageValue.getBytes(UTF_8).length));
    }
    return assertions;
  }

  private static List<AttributeAssertion> messagingAttributes(
      String topic,
      String oldOperation,
      String operationName,
      String operationType,
      String clientIdPrefix) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? oldOperation : null),
                equalTo(
                    MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operationName : null),
                equalTo(
                    MESSAGING_OPERATION_TYPE,
                    emitStableMessagingSemconv() ? operationType : null)));
    if (emitOldMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client_id"), val -> val.startsWith(clientIdPrefix)));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(
          satisfies(stringKey("messaging.client.id"), val -> val.startsWith(clientIdPrefix)));
    }
    return assertions;
  }

  private static void addOffsetAssertion(List<AttributeAssertion> assertions) {
    if (emitOldMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_MESSAGE_OFFSET, AbstractLongAssert::isNotNegative));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    }
  }

  private static void addGroupAssertions(List<AttributeAssertion> assertions) {
    if (emitOldMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_KAFKA_CONSUMER_GROUP, "test"));
    }
    if (emitStableMessagingSemconv()) {
      assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, "test"));
    }
  }

  private static String spanName(String topic, String oldOperation, String operationName) {
    return emitStableMessagingSemconv() ? operationName + " " + topic : topic + " " + oldOperation;
  }

  private static SpanKind receiveKind() {
    return emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER;
  }
}
