/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.java.impl.ClientImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRocketMqClientTest {

  // Inner topic of the container.
  private static final String normalTopic = "normal-topic-0";
  private static final String fifoTopic = "fifo-topic-0";
  private static final String delayTopic = "delay-topic-0";
  private static final String tag = "tagA";
  private static final String consumerGroup = "group-0";

  private static final RocketMqProxyContainer container = new RocketMqProxyContainer();

  private final ClientServiceProvider provider = ClientServiceProvider.loadService();
  private PushConsumer consumer;
  private Producer producer;

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  void setUp() throws ClientException {
    container.start();
    ClientConfiguration clientConfiguration =
        ClientConfiguration.newBuilder()
            .setEndpoints(container.endpoints)
            .setRequestTimeout(Duration.ofSeconds(10))
            .build();
    FilterExpression filterExpression = new FilterExpression(tag, FilterExpressionType.TAG);
    Map<String, FilterExpression> subscriptionExpressions = new HashMap<>();
    subscriptionExpressions.put(normalTopic, filterExpression);
    subscriptionExpressions.put(fifoTopic, filterExpression);
    subscriptionExpressions.put(delayTopic, filterExpression);
    consumer =
        provider
            .newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setSubscriptionExpressions(subscriptionExpressions)
            .setMessageListener(
                messageView -> {
                  testing().runWithSpan("messageListener", () -> {});
                  return ConsumeResult.SUCCESS;
                })
            .build();
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(normalTopic)
            .build();
  }

  @AfterAll
  void tearDown() throws IOException {
    if (producer != null) {
      producer.close();
    }
    if (consumer != null) {
      // Not calling consumer.close(); because it takes a lot of time to complete
      ((ClientImpl) consumer).stopAsync();
    }
    container.close();
  }

  @Test
  void testSendAndConsumeNormalMessage() throws Throwable {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(normalTopic)
            .setTag(tag)
            .setKeys(keys)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent", (ThrowingSupplier<SendReceipt, Throwable>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpan(span, normalTopic, tag, keys, body, sendReceipt)
                          .hasParent(trace.getSpan(0)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, normalTopic, consumerGroup),
                    span ->
                        assertProcessSpan(
                                span,
                                sendSpanData.get(),
                                normalTopic,
                                consumerGroup,
                                tag,
                                keys,
                                body,
                                sendReceipt)
                            // As the child of receive span.
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  public void testSendAsyncMessage() throws Exception {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(normalTopic)
            .setTag(tag)
            .setKeys(keys)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                () ->
                    producer
                        .sendAsync(message)
                        .whenComplete(
                            (result, throwable) -> {
                              testing().runWithSpan("child", () -> {});
                            })
                        .get());
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      assertProducerSpan(span, normalTopic, tag, keys, body, sendReceipt)
                          .hasParent(trace.getSpan(0)),
                  span -> span.hasName("child"));
              sendSpanData.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, normalTopic, consumerGroup),
                    span ->
                        assertProcessSpan(
                                span,
                                sendSpanData.get(),
                                normalTopic,
                                consumerGroup,
                                tag,
                                keys,
                                body,
                                sendReceipt)
                            // As the child of receive span.
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  public void testSendAndConsumeFifoMessage() throws Throwable {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
    String messageGroup = "yourMessageGroup";
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(fifoTopic)
            .setTag(tag)
            .setKeys(keys)
            .setMessageGroup(messageGroup)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent", (ThrowingSupplier<SendReceipt, Throwable>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpanWithFifoMessage(
                              span, fifoTopic, tag, keys, messageGroup, body, sendReceipt)
                          .hasParent(trace.getSpan(0)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, fifoTopic, consumerGroup),
                    span ->
                        assertProcessSpanWithFifoMessage(
                                span,
                                sendSpanData.get(),
                                fifoTopic,
                                consumerGroup,
                                tag,
                                keys,
                                messageGroup,
                                body,
                                sendReceipt)
                            // As the child of receive span.
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  public void testSendAndConsumeDelayMessage() throws Throwable {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
    long deliveryTimestamp = System.currentTimeMillis();
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(delayTopic)
            .setTag(tag)
            .setKeys(keys)
            .setDeliveryTimestamp(deliveryTimestamp)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent", (ThrowingSupplier<SendReceipt, Throwable>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpanWithDelayMessage(
                              span, delayTopic, tag, keys, deliveryTimestamp, body, sendReceipt)
                          .hasParent(trace.getSpan(0)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, delayTopic, consumerGroup),
                    span ->
                        assertProcessSpanWithDelayMessage(
                                span,
                                sendSpanData.get(),
                                delayTopic,
                                consumerGroup,
                                tag,
                                keys,
                                deliveryTimestamp,
                                body,
                                sendReceipt)
                            // As the child of receive span.
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  public void testCapturedMessageHeaders() throws Throwable {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(normalTopic)
            .setTag(tag)
            .setKeys(keys)
            .setBody(body)
            .addProperty("test-message-header", "test")
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent", (ThrowingSupplier<SendReceipt, Throwable>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpan(
                              span,
                              normalTopic,
                              tag,
                              keys,
                              body,
                              sendReceipt,
                              equalTo(
                                  AttributeKey.stringArrayKey(
                                      "messaging.header.test_message_header"),
                                  Collections.singletonList("test")))
                          .hasParent(trace.getSpan(0)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, normalTopic, consumerGroup),
                    span ->
                        assertProcessSpan(
                                span,
                                sendSpanData.get(),
                                normalTopic,
                                consumerGroup,
                                tag,
                                keys,
                                body,
                                sendReceipt,
                                equalTo(
                                    AttributeKey.stringArrayKey(
                                        "messaging.header.test_message_header"),
                                    Collections.singletonList("test")))
                            // As the child of receive span.
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  private static SpanDataAssert assertProducerSpan(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_TYPE,
                    MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues
                        .NORMAL),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "publish")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(topic + " publish")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProducerSpanWithFifoMessage(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      String messageGroup,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_GROUP, messageGroup),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_TYPE,
                    MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues
                        .FIFO),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "publish")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(topic + " publish")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProducerSpanWithDelayMessage(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      long deliveryTimestamp,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, deliveryTimestamp),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_TYPE,
                    MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues
                        .DELAY),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "publish")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(topic + " publish")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertReceiveSpan(
      SpanDataAssert span, String topic, String consumerGroup) {
    return span.hasKind(SpanKind.CONSUMER)
        .hasName(topic + " receive")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup),
            equalTo(MESSAGING_SYSTEM, "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_OPERATION, "receive"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
  }

  private static SpanDataAssert assertProcessSpan(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "process")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.CONSUMER)
        .hasName(topic + " process")
        .hasStatus(StatusData.unset())
        // Link to send span.
        .hasLinks(LinkData.create(linkedSpan.getSpanContext()))
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProcessSpanWithFifoMessage(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      String messageGroup,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_GROUP, messageGroup),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "process")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.CONSUMER)
        .hasName(topic + " process")
        .hasStatus(StatusData.unset())
        // Link to send span.
        .hasLinks(LinkData.create(linkedSpan.getSpanContext()))
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProcessSpanWithDelayMessage(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      long deliveryTimestamp,
      byte[] body,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_ROCKETMQ_CLIENT_GROUP, consumerGroup),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, deliveryTimestamp),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION, "process")));
    attributeAssertions.addAll(Arrays.asList(extraAttributes));

    return span.hasKind(SpanKind.CONSUMER)
        .hasName(topic + " process")
        .hasStatus(StatusData.unset())
        // Link to send span.
        .hasLinks(LinkData.create(linkedSpan.getSpanContext()))
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }
}
