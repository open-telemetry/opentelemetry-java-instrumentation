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
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.Attributes;
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
import java.util.concurrent.CountDownLatch;
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
  private final AtomicReference<CountDownLatch> failureRetryGate = new AtomicReference<>();
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
                  if (failurePending.compareAndSet(true, false)) {
                    testing().runWithSpan("messageListener", () -> {});
                    return ConsumeResult.FAILURE;
                  }
                  CountDownLatch retryGate = failureRetryGate.get();
                  if (retryGate != null) {
                    try {
                      if (!retryGate.await(45, SECONDS)) {
                        return ConsumeResult.FAILURE;
                      }
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return ConsumeResult.FAILURE;
                    }
                  }
                  testing().runWithSpan("messageListener", () -> {});
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
                    span ->
                        assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
    if (emitStableMessagingSemconv()) {
      assertMetrics();
    }
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

    CountDownLatch retryGate = new CountDownLatch(1);
    failureRetryGate.set(retryGate);
    failurePending.set(true);
    SendReceipt sendReceipt;
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    try {
      sendReceipt =
          testing()
              .runWithSpan(
                  "parent",
                  (ThrowingSupplier<SendReceipt, ClientException>) () -> producer.send(message));
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
                      span ->
                          assertReceiveSpan(
                              span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
      if (emitStableMessagingSemconv()) {
        assertFailureMetrics();
      }
      testing().clearData();
    } finally {
      retryGate.countDown();
      failureRetryGate.compareAndSet(retryGate, null);
    }
    waitForSuccessfulRedelivery(sendReceipt);
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
                    span ->
                        assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
                    span ->
                        assertReceiveSpan(span, FIFO_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
                    span ->
                        assertReceiveSpan(span, DELAY_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
                    span ->
                        assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
                bodySize(body),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("send"),
                operationType("send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "send " + topic : topic + " publish")
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
                bodySize(body),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("send"),
                operationType("send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "send " + topic : topic + " publish")
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
                bodySize(body),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("publish"),
                operationName("send"),
                operationType("send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName(emitStableMessagingSemconv() ? "send " + topic : topic + " publish")
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertReceiveSpan(
      SpanDataAssert span, String topic, String consumerGroup) {
    return assertReceiveSpan(span, topic, consumerGroup, null);
  }

  private static SpanDataAssert assertReceiveSpan(
      SpanDataAssert span, String topic, String consumerGroup, SpanData linkedSpan) {
    Attributes linkedAttributes =
        linkedSpan == null ? Attributes.empty() : linkedSpan.getAttributes();
    SpanDataAssert result =
        span.hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
            .hasName(emitStableMessagingSemconv() ? "receive " + topic : topic + " receive")
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(
                equalTo(
                    MESSAGING_CONSUMER_GROUP_NAME,
                    emitStableMessagingSemconv() ? consumerGroup : null),
                equalTo(
                    MESSAGING_ROCKETMQ_CLIENT_GROUP,
                    emitOldMessagingSemconv() ? consumerGroup : null),
                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                namespace(),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                oldOperation("receive"),
                operationName("receive"),
                operationType("receive"),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                // receiving is a batching operation, so the message id is on the link instead
                equalTo(MESSAGING_MESSAGE_ID, null),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_TAG,
                    linkedAttributes.get(MESSAGING_ROCKETMQ_MESSAGE_TAG)),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_GROUP,
                    linkedAttributes.get(MESSAGING_ROCKETMQ_MESSAGE_GROUP)),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP,
                    linkedAttributes.get(MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP)),
                equalTo(
                    MESSAGING_ROCKETMQ_MESSAGE_KEYS,
                    linkedAttributes.get(MESSAGING_ROCKETMQ_MESSAGE_KEYS)));
    if (linkedSpan != null) {
      // one link per received message, carrying the attributes of that message
      result.hasLinks(
          LinkData.create(
              linkedSpan.getSpanContext(),
              Attributes.of(MESSAGING_MESSAGE_ID, linkedAttributes.get(MESSAGING_MESSAGE_ID))));
    }
    return result;
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
                bodySize(body),
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
    return result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
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
                bodySize(body),
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
    return result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
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
                bodySize(body),
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
    return result.hasLinks(LinkData.create(linkedSpan.getSpanContext()));
  }

  private void assertFailureMetrics() {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.process.duration",
            metrics ->
                metrics.satisfiesExactly(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                    equalTo(
                                                        ERROR_TYPE, ConsumeResult.FAILURE.name()),
                                                    equalTo(
                                                        MESSAGING_CONSUMER_GROUP_NAME,
                                                        CONSUMER_GROUP),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME,
                                                        NORMAL_TOPIC))))));
    // messaging.client.consumed.messages is owned by the receive operation here, which the retry
    // gate cannot hold back, so the redelivery would race this assertion; the counter is asserted
    // in testSendAndConsumeNormalMessage instead
  }

  private void waitForSuccessfulRedelivery(SendReceipt sendReceipt) {
    await()
        .atMost(Duration.ofSeconds(45))
        .untilAsserted(
            () ->
                assertThat(testing().spans())
                    .filteredOn(
                        span ->
                            sendReceipt
                                    .getMessageId()
                                    .toString()
                                    .equals(span.getAttributes().get(MESSAGING_MESSAGE_ID))
                                && span.getStatus().equals(StatusData.unset())
                                && ("process"
                                        .equals(span.getAttributes().get(MESSAGING_OPERATION_NAME))
                                    || "process"
                                        .equals(span.getAttributes().get(MESSAGING_OPERATION))))
                    .hasSize(1));
  }

  private void assertMetrics() {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.sent.messages",
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
                                                    equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME,
                                                        NORMAL_TOPIC))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.operation.duration",
            metrics ->
                metrics.satisfiesExactly(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                                                    equalTo(MESSAGING_OPERATION_TYPE, "send")),
                                        // the consumer keeps polling, so the number of recorded
                                        // receives is not deterministic
                                        point ->
                                            point.hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                                equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                                                equalTo(MESSAGING_OPERATION_TYPE, "receive"))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.process.duration",
            metrics ->
                metrics.satisfiesExactly(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                    equalTo(
                                                        MESSAGING_CONSUMER_GROUP_NAME,
                                                        CONSUMER_GROUP),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME,
                                                        NORMAL_TOPIC))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.consumed.messages",
            metrics ->
                metrics.satisfiesExactly(
                    metric ->
                        assertThat(metric)
                            .satisfies(
                                data -> assertThat(data.getLongSumData().getPoints()).hasSize(1))
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                    equalTo(
                                                        MESSAGING_CONSUMER_GROUP_NAME,
                                                        CONSUMER_GROUP),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME,
                                                        NORMAL_TOPIC))))));
    assertThat(testing().metrics())
        .noneMatch(
            metric ->
                metric
                        .getInstrumentationScopeInfo()
                        .getName()
                        .equals("io.opentelemetry.rocketmq-client-5.0")
                    && (metric.getName().equals("messaging.publish.duration")
                        || metric.getName().equals("messaging.receive.duration")
                        || metric.getName().equals("messaging.receive.messages")));
  }

  private static AttributeAssertion bodySize(byte[] body) {
    return equalTo(
        MESSAGING_MESSAGE_BODY_SIZE, emitOldMessagingSemconv() ? (long) body.length : null);
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
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operation : null);
  }
}
