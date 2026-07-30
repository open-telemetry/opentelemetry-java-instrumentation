/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.concurrent.TimeUnit.MINUTES;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.junit.jupiter.api.Test;

class PulsarClientSuppressReceiveSpansTest extends AbstractPulsarClientTest {

  @Test
  void testFailedListenerCountsConsumedMessage() throws Exception {
    String topic = "persistent://public/default/testFailedListenerCountsConsumedMessage";
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
                      latch.countDown();
                      throw new IllegalStateException("test");
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();
    testing.runWithSpan("parent", () -> producer.send("test"));

    assertThat(latch.await(1, MINUTES)).isTrue();

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(metric)
                          .hasLongSumSatisfying(
                              sum ->
                                  sum.hasPointsSatisfying(
                                      point ->
                                          point
                                              .hasValue(1)
                                              .hasAttributesSatisfyingExactly(
                                                  equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                  equalTo(MESSAGING_SYSTEM, "pulsar"),
                                                  equalTo(MESSAGING_DESTINATION_NAME, topic),
                                                  equalTo(
                                                      ERROR_TYPE,
                                                      IllegalStateException.class.getName()))))));
    }
  }

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

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(metric)
                          .hasUnit("{message}")
                          .hasDescription(
                              "Number of messages that were delivered to the application.")
                          .satisfies(
                              data -> assertThat(data.getLongSumData().getPoints()).hasSize(1))
                          .hasLongSumSatisfying(
                              sum ->
                                  sum.hasPointsSatisfying(
                                      point ->
                                          point
                                              .hasValue(1)
                                              .hasAttributesSatisfyingExactly(
                                                  equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                  equalTo(MESSAGING_SYSTEM, "pulsar"),
                                                  equalTo(MESSAGING_DESTINATION_NAME, topic))))));
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("publish", topic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic, msgId.toString(), false)),
                span ->
                    span.hasName(spanName("process", topic))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic, msgId.toString(), false))));
  }

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

    assertDirectReceiveTraces(topic, msgId);
  }

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
                    acknowledgeMessage(consumer, message);
                  }
                });

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    result.get(1, MINUTES);

    assertDirectReceiveTraces(topic, msgId);
  }

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

    assertDirectReceiveTraces(topic, msgId);
  }

  private static void assertDirectReceiveTraces(String topic, MessageId msgId) {
    if (!emitStableMessagingSemconv()) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      span.hasName(spanName("publish", topic))
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              sendAttributes(topic, msgId.toString(), false)),
                  span ->
                      span.hasName(spanName("receive", topic))
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(
                              receiveAttributes(topic, msgId.toString(), false))));
      return;
    }

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName(spanName("publish", topic))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("receive", topic))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false))));
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("publish", topic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic, msgId.toString(), true)),
                span ->
                    span.hasName(spanName("process", topic))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("publish", topic + "-partition-0"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic + "-partition-0", msgId.toString(), false)),
                span ->
                    span.hasName(spanName("process", topic + "-partition-0"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
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

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", "parent2"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent1").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("publish", topic1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic1, msgId1.toString(), false)),
                span ->
                    span.hasName(spanName("process", topic1))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic1, msgId1.toString(), false))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent2").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(spanName("publish", topic2))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic2, msgId2.toString(), false)),
                span ->
                    span.hasName(spanName("process", topic2))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic2, msgId2.toString(), false))));
  }

  private static String spanName(String operation, String destination) {
    return emitStableMessagingSemconv()
        ? operation + " " + destination
        : destination + " " + operation;
  }
}
