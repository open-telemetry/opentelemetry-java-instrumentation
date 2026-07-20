/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues.DELAY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues.FIFO;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingRocketmqMessageTypeIncubatingValues.NORMAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractRocketMqClientTest {

  // Inner topic of the container.
  private static final String NORMAL_TOPIC = "normal-topic-0";
  private static final String FIFO_TOPIC = "fifo-topic-0";
  private static final String DELAY_TOPIC = "delay-topic-0";
  private static final String TAG = "tagA";
  private static final String CONSUMER_GROUP = "group-0";

  private static final RocketMqProxyContainer CONTAINER = new RocketMqProxyContainer();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ClientServiceProvider provider = ClientServiceProvider.loadService();
  private final AtomicBoolean failurePending = new AtomicBoolean();
  private PushConsumer consumer;
  private Producer producer;

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  void setUp() throws ClientException {
    CONTAINER.start();
    cleanup.deferAfterAll(CONTAINER::close);
    ClientConfiguration clientConfiguration =
        ClientConfiguration.newBuilder()
            .setEndpoints(CONTAINER.endpoints)
            .setRequestTimeout(Duration.ofSeconds(10))
            .build();
    FilterExpression filterExpression = new FilterExpression(TAG, FilterExpressionType.TAG);
    Map<String, FilterExpression> subscriptionExpressions = new HashMap<>();
    subscriptionExpressions.put(NORMAL_TOPIC, filterExpression);
    subscriptionExpressions.put(FIFO_TOPIC, filterExpression);
    subscriptionExpressions.put(DELAY_TOPIC, filterExpression);
    consumer =
        provider
            .newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(CONSUMER_GROUP)
            .setSubscriptionExpressions(subscriptionExpressions)
            .setMessageListener(
                messageView -> {
                  testing().runWithSpan("messageListener", () -> {});
                  if (failurePending.compareAndSet(true, false)) {
                    return ConsumeResult.FAILURE;
                  }
                  return ConsumeResult.SUCCESS;
                })
            .build();
    // Not calling consumer.close(); because it takes a lot of time to complete.
    cleanup.deferAfterAll(() -> ((ClientImpl) consumer).stopAsync());
    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(NORMAL_TOPIC)
            .build();
    cleanup.deferAfterAll(producer);
  }

  @Test
  void testSendAndConsumeNormalMessage() throws ClientException {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(NORMAL_TOPIC)
            .setTag(TAG)
            .setKeys(keys)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertProcessSpan(
                                span,
                                trace.getSpan(1),
                                NORMAL_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                body,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertProcessSpan(
                              span,
                              sendSpanData.get(),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              body,
                              sendReceipt)
                          // As the child of receive span.
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
  }

  @Test
  void testConsumeFailure() throws ClientException {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(NORMAL_TOPIC)
            .setTag(TAG)
            .setKeys(keys)
            .setBody(body)
            .build();

    failurePending.set(true);
    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertFailedProcessSpan(
                                span,
                                trace.getSpan(1),
                                NORMAL_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                body,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertFailedProcessSpan(
                              span,
                              sendSpanData.get(),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              body,
                              sendReceipt)
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
  }

  @Test
  void testSendAsyncMessage() {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(NORMAL_TOPIC)
            .setTag(TAG)
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
                        .join());
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent"),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertProcessSpan(
                                span,
                                trace.getSpan(1),
                                NORMAL_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                body,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)),
                    span -> span.hasName("child"));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent"),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span -> span.hasName("child"));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertProcessSpan(
                              span,
                              sendSpanData.get(),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              body,
                              sendReceipt)
                          // As the child of receive span.
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
  }

  @Test
  void testSendAndConsumeFifoMessage() throws ClientException {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    String messageGroup = "yourMessageGroup";
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(FIFO_TOPIC)
            .setTag(TAG)
            .setKeys(keys)
            .setMessageGroup(messageGroup)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpanWithFifoMessage(
                                span, FIFO_TOPIC, TAG, keys, messageGroup, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertProcessSpanWithFifoMessage(
                                span,
                                trace.getSpan(1),
                                FIFO_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                messageGroup,
                                body,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpanWithFifoMessage(
                                span, FIFO_TOPIC, TAG, keys, messageGroup, body, sendReceipt)
                            .hasParent(trace.getSpan(0)));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, FIFO_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, FIFO_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertProcessSpanWithFifoMessage(
                              span,
                              sendSpanData.get(),
                              FIFO_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              messageGroup,
                              body,
                              sendReceipt)
                          // As the child of receive span.
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
  }

  @Test
  void testSendAndConsumeDelayMessage() throws ClientException {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    long deliveryTimestamp = System.currentTimeMillis();
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(DELAY_TOPIC)
            .setTag(TAG)
            .setKeys(keys)
            .setDeliveryTimestamp(deliveryTimestamp)
            .setBody(body)
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpanWithDelayMessage(
                                span, DELAY_TOPIC, TAG, keys, deliveryTimestamp, body, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertProcessSpanWithDelayMessage(
                                span,
                                trace.getSpan(1),
                                DELAY_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                deliveryTimestamp,
                                body,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpanWithDelayMessage(
                                span, DELAY_TOPIC, TAG, keys, deliveryTimestamp, body, sendReceipt)
                            .hasParent(trace.getSpan(0)));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, DELAY_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, DELAY_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertProcessSpanWithDelayMessage(
                              span,
                              sendSpanData.get(),
                              DELAY_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              deliveryTimestamp,
                              body,
                              sendReceipt)
                          // As the child of receive span.
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
  }

  @Test
  void testCapturedMessageHeaders() throws ClientException {
    String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
    byte[] body = "foobar".getBytes(UTF_8);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(NORMAL_TOPIC)
            .setTag(TAG)
            .setKeys(keys)
            .setBody(body)
            .addProperty("Test-Message-Header", "test")
            .addProperty("Uncaptured-Header", "password")
            .build();

    SendReceipt sendReceipt =
        testing()
            .runWithSpan(
                "parent",
                (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(
                SpanKind.INTERNAL, emitStableMessagingSemconv() ? CLIENT : CONSUMER),
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(
                                span,
                                NORMAL_TOPIC,
                                TAG,
                                keys,
                                body,
                                sendReceipt,
                                equalTo(
                                    headerAttributeKey("Test-Message-Header"),
                                    singletonList("test")))
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertProcessSpan(
                                span,
                                trace.getSpan(1),
                                NORMAL_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                body,
                                sendReceipt,
                                equalTo(
                                    headerAttributeKey("Test-Message-Header"),
                                    singletonList("test")))
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(
                                span,
                                NORMAL_TOPIC,
                                TAG,
                                keys,
                                body,
                                sendReceipt,
                                equalTo(
                                    headerAttributeKey("Test-Message-Header"),
                                    singletonList("test")))
                            .hasParent(trace.getSpan(0)));
              }
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              if (emitStableMessagingSemconv()) {
                trace.hasSpansSatisfyingExactly(
                    span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP));
                return;
              }
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP),
                  span ->
                      assertProcessSpan(
                              span,
                              sendSpanData.get(),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              body,
                              sendReceipt,
                              equalTo(
                                  headerAttributeKey("Test-Message-Header"), singletonList("test")))
                          // As the child of receive span.
                          .hasParent(trace.getSpan(0)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(1)));
            });
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
            asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TYPE, NORMAL),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("publish"),
                operationType("publish")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "publish " + topic : topic + " publish")
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
            asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_GROUP, messageGroup),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TYPE, FIFO),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("publish"),
                operationType("publish")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "publish " + topic : topic + " publish")
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
            asList(
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, deliveryTimestamp),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TYPE, DELAY),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("publish"),
                operationType("publish")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "publish " + topic : topic + " publish")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertReceiveSpan(
      SpanDataAssert span, String topic, String consumerGroup) {
    return span.hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
        .hasName(emitStableMessagingSemconv() ? "receive " + topic : topic + " receive")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(
                MESSAGING_CONSUMER_GROUP_NAME, emitStableMessagingSemconv() ? consumerGroup : null),
            equalTo(
                MESSAGING_ROCKETMQ_CLIENT_GROUP, emitOldMessagingSemconv() ? consumerGroup : null),
            equalTo(MESSAGING_SYSTEM, "rocketmq"),
            namespace(),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            oldOperation("receive"),
            operationName("receive"),
            operationType("receive"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1));
  }

  private static SpanDataAssert assertFailedProcessSpan(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      byte[] body,
      SendReceipt sendReceipt) {
    return assertProcessSpan(
        span,
        linkedSpan,
        topic,
        consumerGroup,
        tag,
        keys,
        body,
        sendReceipt,
        StatusData.error(),
        equalTo(ERROR_TYPE, emitStableMessagingSemconv() ? "FAILURE" : null));
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
    return assertProcessSpan(
        span,
        linkedSpan,
        topic,
        consumerGroup,
        tag,
        keys,
        body,
        sendReceipt,
        StatusData.unset(),
        extraAttributes);
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
      StatusData status,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME,
                    emitStableMessagingSemconv() ? consumerGroup : null),
                equalTo(
                    MESSAGING_ROCKETMQ_CLIENT_GROUP,
                    emitOldMessagingSemconv() ? consumerGroup : null),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("process"),
                operationName("process"),
                operationType("process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName(emitStableMessagingSemconv() ? "process " + topic : topic + " process")
            .hasStatus(status)
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return emitStableMessagingSemconv()
        ? result.hasTotalRecordedLinks(0)
        : result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
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
            asList(
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME,
                    emitStableMessagingSemconv() ? consumerGroup : null),
                equalTo(
                    MESSAGING_ROCKETMQ_CLIENT_GROUP,
                    emitOldMessagingSemconv() ? consumerGroup : null),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_GROUP, messageGroup),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("process"),
                operationName("process"),
                operationType("process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName(emitStableMessagingSemconv() ? "process " + topic : topic + " process")
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return emitStableMessagingSemconv()
        ? result.hasTotalRecordedLinks(0)
        : result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
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
            asList(
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME,
                    emitStableMessagingSemconv() ? consumerGroup : null),
                equalTo(
                    MESSAGING_ROCKETMQ_CLIENT_GROUP,
                    emitOldMessagingSemconv() ? consumerGroup : null),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, asList(keys)),
                equalTo(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP, deliveryTimestamp),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("process"),
                operationName("process"),
                operationType("process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName(emitStableMessagingSemconv() ? "process " + topic : topic + " process")
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return emitStableMessagingSemconv()
        ? result.hasTotalRecordedLinks(0)
        : result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion namespace() {
    return equalTo(MESSAGING_ROCKETMQ_NAMESPACE, emitStableMessagingSemconv() ? "" : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    String operationType = operation.equals("publish") ? "send" : operation;
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationType : null);
  }
}
