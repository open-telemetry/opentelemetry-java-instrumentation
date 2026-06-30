/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.MessageRouter;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.api.TopicMetadata;
import org.apache.pulsar.client.api.transaction.Transaction;
import org.junit.jupiter.api.Test;

class PulsarClientTest extends AbstractPulsarClientTest {

  @Test
  void testConsumeNonPartitionedTopic() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopic";
    CountDownLatch latch = new CountDownLatch(1);
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    latch.await(1, MINUTES);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false)),
                span ->
                    span.hasName(topic + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic, msgId.toString(), false))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testConsumeNonPartitionedTopicUsingReceive() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceive";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    Message<String> receivedMsg = consumer.receive();
    consumer.acknowledge(receivedMsg);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false))));

    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                !metric.getName().startsWith("otel.sdk.")
                    && !metric.getName().startsWith("pulsar.client."))
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of receive operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "receive"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))),
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.messages")
                    .hasUnit("{message}")
                    .hasDescription("Measures the number of received messages.")
                    .hasLongSumSatisfying(
                        sum -> {
                          sum.hasPointsSatisfying(
                              point -> {
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_SYSTEM, "pulsar"),
                                        equalTo(MESSAGING_DESTINATION_NAME, topic),
                                        equalTo(MESSAGING_OPERATION, "receive"),
                                        equalTo(SERVER_PORT, brokerPort),
                                        equalTo(SERVER_ADDRESS, brokerHost));
                              });
                        }),
            metric ->
                assertThat(metric)
                    .hasName("messaging.publish.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of publish operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "publish"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testConsumeNonPartitionedTopicUsingReceiveAsync() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveAsync";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    CompletableFuture<Message<String>> result =
        consumer
            .receiveAsync()
            .whenComplete(
                (message, throwable) -> {
                  if (message != null) {
                    testing.runWithSpan("callback", () -> acknowledgeMessage(consumer, message));
                  }
                });

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    result.get(1, MINUTES);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));

    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                !metric.getName().startsWith("otel.sdk.")
                    && !metric.getName().startsWith("pulsar.client."))
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of receive operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "receive"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))),
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.messages")
                    .hasUnit("{message}")
                    .hasDescription("Measures the number of received messages.")
                    .hasLongSumSatisfying(
                        sum -> {
                          sum.hasPointsSatisfying(
                              point -> {
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_SYSTEM, "pulsar"),
                                        equalTo(MESSAGING_DESTINATION_NAME, topic),
                                        equalTo(MESSAGING_OPERATION, "receive"),
                                        equalTo(SERVER_PORT, brokerPort),
                                        equalTo(SERVER_ADDRESS, brokerHost));
                              });
                        }),
            metric ->
                assertThat(metric)
                    .hasName("messaging.publish.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of publish operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "publish"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testConsumeNonPartitionedTopicUsingReceiveWithTimeout() throws Exception {
    String topic =
        "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveWithTimeout";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    Message<String> receivedMsg = consumer.receive(1, MINUTES);
    consumer.acknowledge(receivedMsg);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false))));

    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                !metric.getName().startsWith("otel.sdk.")
                    && !metric.getName().startsWith("pulsar.client."))
        .satisfiesExactlyInAnyOrder(
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of receive operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "receive"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))),
            metric ->
                assertThat(metric)
                    .hasName("messaging.receive.messages")
                    .hasUnit("{message}")
                    .hasDescription("Measures the number of received messages.")
                    .hasLongSumSatisfying(
                        sum -> {
                          sum.hasPointsSatisfying(
                              point -> {
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_SYSTEM, "pulsar"),
                                        equalTo(MESSAGING_DESTINATION_NAME, topic),
                                        equalTo(MESSAGING_OPERATION, "receive"),
                                        equalTo(SERVER_PORT, brokerPort),
                                        equalTo(SERVER_ADDRESS, brokerHost));
                              });
                        }),
            metric ->
                assertThat(metric)
                    .hasName("messaging.publish.duration")
                    .hasUnit("s")
                    .hasDescription("Measures the duration of publish operation.")
                    .hasHistogramSatisfying(
                        histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasSumGreaterThan(0.0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_SYSTEM, "pulsar"),
                                            equalTo(MESSAGING_DESTINATION_NAME, topic),
                                            equalTo(MESSAGING_OPERATION, "publish"),
                                            equalTo(SERVER_PORT, brokerPort),
                                            equalTo(SERVER_ADDRESS, brokerHost))
                                        .hasBucketBoundaries(DURATION_BUCKETS))));
  }

  @Test
  void captureMessageHeaderAsSpanAttribute() throws Exception {
    String topic = "persistent://public/default/testCaptureMessageHeaderTopic";
    CountDownLatch latch = new CountDownLatch(1);
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId =
        testing.runWithSpan(
            "parent",
            () ->
                producer
                    .newMessage()
                    .value(msg)
                    .property("Test-Message-Header", "test")
                    .property("Uncaptured-Header", "password")
                    .send());

    latch.await(1, MINUTES);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), true)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), true)),
                span ->
                    span.hasName(topic + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic, msgId.toString(), true))));
  }

  @Test
  void testConsumePartitionedTopic() throws Exception {
    String topic = "persistent://public/default/testConsumePartitionedTopic";
    admin.topics().createPartitionedTopic(topic, 1);
    CountDownLatch latch = new CountDownLatch(1);

    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topic(topic)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    latch.await(1, MINUTES);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic + "-partition-0 publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic + "-partition-0", msgId.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic + "-partition-0 receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic + "-partition-0", msgId.toString(), false)),
                span ->
                    span.hasName(topic + "-partition-0 process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic + "-partition-0", msgId.toString(), false))));
  }

  @Test
  void testConsumeMultiTopics() throws Exception {
    String topicNamePrefix = "persistent://public/default/testConsumeMulti_";
    String topic1 = topicNamePrefix + "1";
    String topic2 = topicNamePrefix + "2";
    CountDownLatch latch = new CountDownLatch(2);
    producer = client.newProducer(Schema.STRING).topic(topic1).enableBatching(false).create();
    producer2 = client.newProducer(Schema.STRING).topic(topic2).enableBatching(false).create();

    MessageId msgId1 = testing.runWithSpan("parent1", () -> producer.send("test1"));
    MessageId msgId2 = testing.runWithSpan("parent2", () -> producer2.send("test2"));

    consumer =
        client
            .newConsumer(Schema.STRING)
            .topic(topic2, topic1)
            .subscriptionName("test_sub")
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    latch.await(1, MINUTES);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    AtomicReference<SpanData> producerSpan2 = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", topic1 + " receive", "parent2", topic2 + " receive"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent1").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic1 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic1, msgId1.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic1 + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic1, msgId1.toString(), false)),
                span ->
                    span.hasName(topic1 + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic1, msgId1.toString(), false))),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent2").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic2 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic2, msgId2.toString(), false)));

          producerSpan2.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic2 + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic2, msgId2.toString(), false)),
                span ->
                    span.hasName(topic2 + " process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic2, msgId2.toString(), false))));
  }

  @Test
  void testReceiveMultiTopics() throws Exception {
    String topicNamePrefix = "persistent://public/default/testReceiveMulti_";
    String topic1 = topicNamePrefix + "1";
    String topic2 = topicNamePrefix + "2";
    producer = client.newProducer(Schema.STRING).topic(topic1).enableBatching(false).create();
    producer2 = client.newProducer(Schema.STRING).topic(topic2).enableBatching(false).create();

    MessageId msgId1 = testing.runWithSpan("parent1", () -> producer.send("test1"));
    MessageId msgId2 = testing.runWithSpan("parent2", () -> producer2.send("test2"));

    consumer =
        client
            .newConsumer(Schema.STRING)
            .topic(topic2, topic1)
            .subscriptionName("test_sub")
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    Message<String> received1 = consumer.receive(1, MINUTES);
    Message<String> received2 = consumer.receive(1, MINUTES);
    consumer.acknowledge(received1);
    consumer.acknowledge(received2);

    assertThat(asList(received1.getMessageId().toString(), received2.getMessageId().toString()))
        .containsExactlyInAnyOrder(msgId1.toString(), msgId2.toString());

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    AtomicReference<SpanData> producerSpan2 = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", topic1 + " receive", "parent2", topic2 + " receive"),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent1").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic1 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic1, msgId1.toString(), false)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic1 + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic1, msgId1.toString(), false))),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent2").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(topic2 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic2, msgId2.toString(), false)));

          producerSpan2.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(topic2 + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan2.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic2, msgId2.toString(), false))));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testConsumePartitionedTopicUsingBatchReceive() throws Exception {
    String topic = "persistent://public/default/testConsumePartitionedTopicUsingBatchReceive";
    admin.topics().createPartitionedTopic(topic, 4);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .enableBatching(false)
            .messageRouter(
                new MessageRouter() {
                  @Override
                  public int choosePartition(Message<?> message) {
                    return Integer.parseInt(message.getKey());
                  }

                  @Override
                  public int choosePartition(Message<?> message, TopicMetadata metadata) {
                    return choosePartition(message);
                  }
                })
            .create();

    String msg = "test";
    for (int i = 0; i < 4; i++) {
      producer.newMessage().key(String.valueOf(i)).value(msg).send();
    }

    Thread.sleep(1_000); // wait so that messages would be received as one batch

    Messages<String> receivedMsg = consumer.batchReceive();
    consumer.acknowledge(receivedMsg);
    assertThat(receivedMsg).hasSize(4);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.pulsar-2.8",
        "messaging.receive.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription("Measures the number of received messages.")
                        .satisfies(
                            data ->
                                assertThat(data.getLongSumData().getPoints())
                                    .hasSize(1)
                                    .allSatisfy(
                                        point -> {
                                          assertThat(point.getValue()).isEqualTo(4);
                                          assertThat(
                                                  point
                                                      .getAttributes()
                                                      .get(MESSAGING_DESTINATION_NAME))
                                              .isEqualTo(topic);
                                          assertThat(point.getAttributes().get(MESSAGING_OPERATION))
                                              .isEqualTo("receive");
                                          assertThat(point.getAttributes().get(MESSAGING_SYSTEM))
                                              .isEqualTo("pulsar");
                                          assertThat(point.getAttributes().get(SERVER_PORT))
                                              .isEqualTo((long) brokerPort);
                                          assertThat(point.getAttributes().get(SERVER_ADDRESS))
                                              .isEqualTo(brokerHost);
                                        }))));
  }

  @Test
  void testSendMessageWithTxn() throws Exception {
    String topic = "persistent://public/default/testSendMessageWithTxn";
    admin.topics().createNonPartitionedTopic(topic);
    producer =
        client
            .newProducer(Schema.STRING)
            .topic(topic)
            .sendTimeout(0, SECONDS)
            .enableBatching(false)
            .create();
    Transaction transaction =
        client.newTransaction().withTransactionTimeout(15, SECONDS).build().get();
    testing.runWithSpan("parent1", () -> producer.newMessage(transaction).value("test1").send());
    transaction.commit();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent1").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(topic + " publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))));
  }
}
