/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertReceiveMetrics;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_CLUSTER_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.AbstractSpringKafkaTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringKafkaTest extends AbstractSpringKafkaTest {

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.kafka.experimental-span-attributes");

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<Class<?>> additionalSpringConfigs() {
    return emptyList();
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testSingleTopic", "10", "testSpan");
                return 0;
              });
        });

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("send testSingleTopic")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testSingleTopic", "10")),
              span ->
                  span.hasName("process testSingleTopic")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(1))
                      .hasLinks(LinkData.create(asRemote(trace.getSpan(1).getSpanContext())))
                      .hasAttributesSatisfyingExactly(
                          singleProcessAttributes("testSingleTopic", "testSingleListener", "10")),
              span -> span.hasName("consumer").hasParent(trace.getSpan(2)));
          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("poll testSingleTopic")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasLinks(recordLink(producer.get()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes("testSingleTopic", "testSingleListener", 1))));
    assertSingleMetrics();
  }

  @Test
  void shouldTraceRawConsumerInsideListener() {
    assertRawConsumerInsideListener(
        "testNestedTopic", "nested", this::runOnNextNestedRecord, "testSingleTopic");
  }

  @Test
  void shouldTraceRawConsumerInsideBatchListener() {
    assertRawConsumerInsideListener(
        "testNestedBatchTopic", "nested-batch", this::runOnNextNestedBatch, "testBatchTopic");
  }

  private void assertRawConsumerInsideListener(
      String nestedTopic,
      String groupId,
      Consumer<Runnable> callbackRegistrar,
      String listenerTopic) {
    Map<String, Object> consumerProperties = new HashMap<>();
    consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProperties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

    callbackRegistrar.accept(
        () -> {
          Span listenerSpan = Span.current();
          try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            List<TopicPartition> partitions = singletonList(new TopicPartition(nestedTopic, 0));
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            ConsumerRecords<String, String> records;
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            do {
              records = consumer.poll(Duration.ofSeconds(1));
            } while (records.isEmpty() && System.nanoTime() < deadline);
            assertThat(records.count()).isEqualTo(1);
            Iterator<?> iterator = records.iterator();
            Object record = iterator.next();
            assertThat(Span.current().getSpanContext().getSpanId())
                .isNotEqualTo(listenerSpan.getSpanContext().getSpanId());
            testing.runWithSpan("nested processing", () -> assertThat(record).isNotNull());
            assertThat(iterator.hasNext()).isFalse();
            assertThat(Span.current().getSpanContext().getSpanId())
                .isEqualTo(listenerSpan.getSpanContext().getSpanId());
          }
        });

    kafkaTemplate.executeInTransaction(
        operations -> {
          send(nestedTopic, "nested-key", "nested-value");
          return null;
        });
    kafkaTemplate.executeInTransaction(
        operations -> {
          send(listenerTopic, "10", "nested");
          return null;
        });

    String nestedProcessName = "process " + nestedTopic;
    String nestedReceiveName = "poll " + nestedTopic;
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().equals(nestedReceiveName))
                  .hasSize(1);
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().equals(nestedProcessName))
                  .hasSize(1);
              assertThat(testing.spans())
                  .filteredOn(span -> span.getName().equals("nested processing"))
                  .singleElement()
                  .satisfies(
                      span ->
                          assertThat(span.getParentSpanId())
                              .isEqualTo(
                                  testing.spans().stream()
                                      .filter(
                                          candidate ->
                                              candidate.getName().equals(nestedProcessName))
                                      .findFirst()
                                      .orElseThrow(AssertionError::new)
                                      .getSpanId()));
            });

    assertReceiveMetrics(
        testing, "io.opentelemetry.kafka-clients-0.11", nestedTopic, groupId, "0", 1, 1, null);
    assertProcessMetrics(
        testing, "io.opentelemetry.kafka-clients-0.11", nestedTopic, groupId, "0", 1, null);
  }

  @Test
  void shouldHandleFailureInKafkaListener() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testSingleTopic", "10", "error");
                return 0;
              });
        });

    AtomicReference<SpanData> producer = new AtomicReference<>();
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    assertions.add(
        trace -> {
          List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
          spanAssertions.add(span -> span.hasName("producer"));
          spanAssertions.add(
              span ->
                  span.hasName("send testSingleTopic")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testSingleTopic", "10")));
          // trace structure differs in latest dep tests because CommonErrorHandler is
          // only set for latest dep tests
          addSingleProcessAssertions(spanAssertions, trace, 2, true, testLatestDeps());
          addSingleProcessAssertions(
              spanAssertions, trace, testLatestDeps() ? 5 : 4, true, testLatestDeps());
          addSingleProcessAssertions(spanAssertions, trace, testLatestDeps() ? 8 : 6, false, false);
          trace.hasSpansSatisfyingExactly(spanAssertions);
          producer.set(trace.getSpan(1));
        });
    int receiveCount = testLatestDeps() ? 1 : 3;
    for (int i = 0; i < receiveCount; i++) {
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      assertReceiveSpan(
                          span, producer.get(), "testSingleTopic", "testSingleListener")));
    }
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT), assertions);
    assertSingleFailureMetrics();
  }

  @Test
  void shouldCreateSpansForBatchReceiveAndProcess() throws InterruptedException {
    Map<String, String> batchMessages = new HashMap<>();
    batchMessages.put("10", "testSpan1");
    batchMessages.put("20", "testSpan2");
    sendBatchMessages(batchMessages);

    AtomicReference<SpanData> producer1 = new AtomicReference<>();
    AtomicReference<SpanData> producer2 = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactlyInAnyOrder(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("send testBatchTopic")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "10")),
              span ->
                  span.hasName("send testBatchTopic")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "20")));
          producer1.set(trace.getSpan(1));
          producer2.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process testBatchTopic")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(recordLink(producer1.get()), recordLink(producer2.get()))
                        .hasAttributesSatisfyingExactly(
                            batchProcessAttributes("testBatchTopic", "testBatchListener", 2)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("poll testBatchTopic")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasLinks(recordLink(producer1.get()), recordLink(producer2.get()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes("testBatchTopic", "testBatchListener", 2))));
    assertBatchMetrics();
  }

  private static void assertSingleMetrics() {
    assertReceiveMetrics(
        testing,
        "io.opentelemetry.kafka-clients-0.11",
        "testSingleTopic",
        "testSingleListener",
        "0",
        1,
        1,
        null);
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        1,
        null);
  }

  private static void assertBatchMetrics() {
    assertReceiveMetrics(
        testing,
        "io.opentelemetry.kafka-clients-0.11",
        "testBatchTopic",
        "testBatchListener",
        "0",
        1,
        2,
        null);
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testBatchTopic",
        "testBatchListener",
        "0",
        1,
        null);
  }

  @Test
  void shouldHandleFailureInKafkaBatchListener() {
    testing.runWithSpan(
        "producer",
        () -> {
          kafkaTemplate.executeInTransaction(
              ops -> {
                send("testBatchTopic", "10", "error");
                return 0;
              });
        });

    AtomicReference<SpanData> producer = new AtomicReference<>();

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    assertions.add(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("send testBatchTopic")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(producerAttributes("testBatchTopic", "10")));
          producer.set(trace.getSpan(1));
        });
    assertions.add(trace -> assertBatchProcessTrace(trace, producer.get(), true));
    assertions.add(trace -> assertBatchProcessTrace(trace, producer.get(), true));
    assertions.add(trace -> assertBatchProcessTrace(trace, producer.get(), false));
    // latest dep tests call receive once and only retry the failed process step
    int receiveCount = testLatestDeps() ? 1 : 3;
    for (int i = 0; i < receiveCount; i++) {
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      assertReceiveSpan(
                          span, producer.get(), "testBatchTopic", "testBatchListener")));
    }
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER, SpanKind.CLIENT), assertions);
    assertBatchFailureMetrics();
  }

  private static void assertSingleFailureMetrics() {
    int receiveCount = testLatestDeps() ? 1 : 3;
    assertReceiveMetrics(
        testing,
        "io.opentelemetry.kafka-clients-0.11",
        "testSingleTopic",
        "testSingleListener",
        "0",
        receiveCount,
        receiveCount,
        null);
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        2,
        IllegalArgumentException.class.getName());
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testSingleTopic",
        "testSingleListener",
        "0",
        1,
        null);
  }

  private static void assertBatchFailureMetrics() {
    int receiveCount = testLatestDeps() ? 1 : 3;
    assertReceiveMetrics(
        testing,
        "io.opentelemetry.kafka-clients-0.11",
        "testBatchTopic",
        "testBatchListener",
        "0",
        receiveCount,
        receiveCount,
        null);
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testBatchTopic",
        "testBatchListener",
        "0",
        2,
        IllegalArgumentException.class.getName());
    assertProcessMetrics(
        testing,
        "io.opentelemetry.spring-kafka-2.7",
        "testBatchTopic",
        "testBatchListener",
        "0",
        1,
        null);
  }

  private static void addSingleProcessAssertions(
      List<Consumer<SpanDataAssert>> assertions,
      TraceAssert trace,
      int processIndex,
      boolean failed,
      boolean addExceptionHandler) {
    List<AttributeAssertion> processAttributes =
        singleProcessAttributes("testSingleTopic", "testSingleListener", "10");
    assertions.add(
        span -> {
          span.hasName("process testSingleTopic")
              .hasKind(SpanKind.CONSUMER)
              .hasParent(trace.getSpan(1))
              .hasLinks(LinkData.create(asRemote(trace.getSpan(1).getSpanContext())))
              .hasAttributesSatisfyingExactly(withErrorType(processAttributes, failed));
          if (failed) {
            span.hasStatus(StatusData.error()).hasException(new IllegalArgumentException("boom"));
          }
        });
    assertions.add(span -> span.hasName("consumer").hasParent(trace.getSpan(processIndex)));
    if (addExceptionHandler) {
      assertions.add(
          span -> span.hasName("handle exception").hasParent(trace.getSpan(processIndex)));
    }
  }

  private static void assertBatchProcessTrace(
      TraceAssert trace, SpanData producer, boolean failed) {
    trace.hasSpansSatisfyingExactly(
        span -> {
          span.hasName("process testBatchTopic")
              .hasKind(SpanKind.CONSUMER)
              .hasNoParent()
              .hasLinks(recordLink(producer))
              .hasAttributesSatisfyingExactly(
                  withErrorType(
                      batchProcessAttributes("testBatchTopic", "testBatchListener", 1), failed));
          if (failed) {
            span.hasStatus(StatusData.error()).hasException(new IllegalArgumentException("boom"));
          }
        },
        span -> span.hasName("consumer").hasParent(trace.getSpan(0)));
  }

  private static void assertReceiveSpan(
      SpanDataAssert span, SpanData producer, String topic, String group) {
    span.hasName("poll " + topic)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasLinks(recordLink(producer))
        .hasAttributesSatisfyingExactly(receiveAttributes(topic, group, 1));
  }

  private static List<AttributeAssertion> producerAttributes(String topic, String messageKey) {
    List<AttributeAssertion> assertions = messagingAttributes(topic, "send", "send", "producer");
    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(
        equalTo(
            stringKey("messaging.kafka.bootstrap.servers"),
            EXPERIMENTAL_ATTRIBUTES ? kafka.getBootstrapServers() : null));
    return assertions;
  }

  private static List<AttributeAssertion> receiveAttributes(
      String topic, String group, int batchSize) {
    List<AttributeAssertion> assertions = messagingAttributes(topic, "poll", "receive", "consumer");
    assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    addCommonBatchRecordAttributes(assertions);
    return assertions;
  }

  private static List<AttributeAssertion> singleProcessAttributes(
      String topic, String group, String messageKey) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "consumer");

    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
    assertions.add(satisfies(MESSAGING_KAFKA_OFFSET, AbstractLongAssert::isNotNegative));
    assertions.add(equalTo(MESSAGING_KAFKA_MESSAGE_KEY, messageKey));
    assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    assertions.add(
        satisfies(
            longKey("kafka.record.queue_time_ms"),
            val -> {
              if (EXPERIMENTAL_ATTRIBUTES) {
                val.isNotNegative();
              }
            }));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    return assertions;
  }

  private static List<AttributeAssertion> batchProcessAttributes(
      String topic, String group, int batchSize) {
    List<AttributeAssertion> assertions =
        messagingAttributes(topic, "process", "process", "consumer");
    assertions.add(equalTo(MESSAGING_CONSUMER_GROUP_NAME, group));
    assertions.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize));
    assertions.add(satisfies(MESSAGING_KAFKA_CLUSTER_ID, AbstractStringAssert::isNotEmpty));
    addCommonBatchRecordAttributes(assertions);
    return assertions;
  }

  private static void addCommonBatchRecordAttributes(List<AttributeAssertion> assertions) {

    assertions.add(satisfies(MESSAGING_DESTINATION_PARTITION_ID, AbstractStringAssert::isNotEmpty));
  }

  private static List<AttributeAssertion> messagingAttributes(
      String topic, String operationName, String operationType, String clientIdPrefix) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_SYSTEM, "kafka"),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, operationName),
                equalTo(MESSAGING_OPERATION_TYPE, operationType)));

    assertions.add(satisfies(MESSAGING_CLIENT_ID, val -> val.startsWith(clientIdPrefix)));
    return assertions;
  }

  private static List<AttributeAssertion> withErrorType(
      List<AttributeAssertion> assertions, boolean failed) {
    List<AttributeAssertion> result = new ArrayList<>(assertions);
    if (failed) {
      result.add(equalTo(ERROR_TYPE, IllegalArgumentException.class.getName()));
    }
    return result;
  }
}
