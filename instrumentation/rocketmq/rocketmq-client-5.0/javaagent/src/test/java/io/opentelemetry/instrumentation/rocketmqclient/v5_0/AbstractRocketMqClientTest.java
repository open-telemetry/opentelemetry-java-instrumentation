/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.RocketmqIncubatingAttributes.RocketmqMessageTypeIncubatingValues.DELAY;
import static io.opentelemetry.semconv.incubating.RocketmqIncubatingAttributes.RocketmqMessageTypeIncubatingValues.FIFO;
import static io.opentelemetry.semconv.incubating.RocketmqIncubatingAttributes.RocketmqMessageTypeIncubatingValues.NORMAL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import apache.rocketmq.v2.Code;
import apache.rocketmq.v2.SendMessageRequest;
import apache.rocketmq.v2.SendMessageResponse;
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
import java.util.concurrent.atomic.AtomicInteger;
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
import org.apache.rocketmq.client.java.exception.InternalErrorException;
import org.apache.rocketmq.client.java.impl.ClientImpl;
import org.apache.rocketmq.client.java.impl.ClientManager;
import org.apache.rocketmq.client.java.route.Endpoints;
import org.apache.rocketmq.client.java.rpc.RpcFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

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
            .setMessageListener(messageView -> {
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
            .setMaxAttempts(3)
            .build();
    cleanup.deferAfterAll(producer);
  }

  @Test
  void testSendAndConsumeNormalMessage() throws ClientException {
    sendAndConsumeNormalMessage(producer);
  }

  @Test
  void testSendRetriesAndConsumesMessage() throws ClientException {
    AtomicInteger attempts = new AtomicInteger();

    sendAndConsumeNormalMessage(producerWithSendFailures(2, attempts));

    assertThat(attempts).hasValue(3);
  }

  @Test
  void testSendRetriesExhausted() {
    AtomicInteger attempts = new AtomicInteger();
    Producer sender = producerWithSendFailures(3, attempts);
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(NORMAL_TOPIC)
            .setTag(TAG)
            .setKeys("retry-key")
            .setBody("message".getBytes(UTF_8))
            .build();

    testing()
        .runWithSpan(
            "parent",
            () ->
                assertThatThrownBy(() -> sender.send(message))
                    .isInstanceOf(InternalErrorException.class));

    assertThat(attempts).hasValue(3);

    testing()
        .waitAndAssertTraces(trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + NORMAL_TOPIC)
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            satisfies(MESSAGING_MESSAGE_ID, val -> val.isNotEmpty()),
                            equalTo(stringKey("messaging.system.name"), "rocketmq"),
                            equalTo(stringKey("rocketmq.message.type"), NORMAL),
                            equalTo(stringKey("rocketmq.message.tag"), TAG),
                            equalTo(
                                stringArrayKey("rocketmq.message.keys"),
                                singletonList("retry-key")),
                            equalTo(ERROR_TYPE, InternalErrorException.class.getName()))));

    assertFailedSendMetrics();
  }

  @SuppressWarnings("MockitoDoSetup") // when() would invoke the real RPC with matcher arguments.
  private Producer producerWithSendFailures(int failures, AtomicInteger attempts) {
    ClientManager realManager = ((ClientImpl) producer).getClientManager();
    ClientManager manager = mock(ClientManager.class, delegatesTo(realManager));

    doAnswer(
            invocation -> {
              if (attempts.incrementAndGet() <= failures) {
                return new RpcFuture<SendMessageRequest, SendMessageResponse>(
                    new InternalErrorException(
                        Code.INTERNAL_ERROR.getNumber(), "retry-test", "send failed"));
              }

              return realManager.sendMessage(
                  invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            })
        .when(manager)
        .sendMessage(any(Endpoints.class), any(SendMessageRequest.class), any(Duration.class));

    Producer sender = spy(producer);
    when(((ClientImpl) sender).getClientManager()).thenReturn(manager);
    return sender;
  }

  private void sendAndConsumeNormalMessage(Producer sender) throws ClientException {
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
                (ThrowingSupplier<SendReceipt, ClientException>) () -> sender.send(message));
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, sendReceipt)
                          .hasParent(trace.getSpan(0)),
                  span ->
                      assertProcessSpan(
                              span,
                              trace.getSpan(1),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              sendReceipt)
                          .hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(2)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              trace.hasSpansSatisfyingExactly(span ->
                  assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
            });
    assertMetrics();
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
              orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
              trace -> {
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, sendReceipt)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        assertFailedProcessSpan(
                                span,
                                trace.getSpan(1),
                                NORMAL_TOPIC,
                                CONSUMER_GROUP,
                                TAG,
                                keys,
                                sendReceipt)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)));
                sendSpanData.set(trace.getSpan(1));
              },
              trace -> {
                trace.hasSpansSatisfyingExactly(span ->
                    assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
              });
      assertFailureMetrics();
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
                        .whenComplete((result, throwable) -> {
                          testing().runWithSpan("child", () -> {});
                        })
                        .join());
    AtomicReference<SpanData> sendSpanData = new AtomicReference<>();
    testing()
        .waitAndAssertSortedTraces(
            orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      assertProducerSpan(span, NORMAL_TOPIC, TAG, keys, sendReceipt)
                          .hasParent(trace.getSpan(0)),
                  span ->
                      assertProcessSpan(
                              span,
                              trace.getSpan(1),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              sendReceipt)
                          .hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(2)),
                  span -> span.hasName("child"));
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              trace.hasSpansSatisfyingExactly(span ->
                  assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
            orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpanWithFifoMessage(
                              span, FIFO_TOPIC, TAG, keys, messageGroup, sendReceipt)
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
                              sendReceipt)
                          .hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(2)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, FIFO_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
            orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpanWithDelayMessage(
                              span, DELAY_TOPIC, TAG, keys, deliveryTimestamp, sendReceipt)
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
                              sendReceipt)
                          .hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(2)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> assertReceiveSpan(span, DELAY_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
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
            orderByRootSpanKind(SpanKind.INTERNAL, CLIENT),
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span ->
                      assertProducerSpan(
                              span,
                              NORMAL_TOPIC,
                              TAG,
                              keys,
                              sendReceipt,
                              equalTo(
                                  headerAttributeKey("Test-Message-Header"), singletonList("test")))
                          .hasParent(trace.getSpan(0)),
                  span ->
                      assertProcessSpan(
                              span,
                              trace.getSpan(1),
                              NORMAL_TOPIC,
                              CONSUMER_GROUP,
                              TAG,
                              keys,
                              sendReceipt,
                              equalTo(
                                  headerAttributeKey("Test-Message-Header"), singletonList("test")))
                          .hasParent(trace.getSpan(1)),
                  span ->
                      span.hasName("messageListener")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(2)));
              sendSpanData.set(trace.getSpan(1));
            },
            trace -> {
              trace.hasSpansSatisfyingExactly(span ->
                  assertReceiveSpan(span, NORMAL_TOPIC, CONSUMER_GROUP, sendSpanData.get()));
            });
  }

  private static SpanDataAssert assertProducerSpan(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(stringKey("rocketmq.message.type"), NORMAL),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName("send " + topic)
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProducerSpanWithFifoMessage(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      String messageGroup,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(stringKey("rocketmq.message.group"), messageGroup),
                equalTo(stringKey("rocketmq.message.type"), FIFO),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName("send " + topic)
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertProducerSpanWithDelayMessage(
      SpanDataAssert span,
      String topic,
      String tag,
      String[] keys,
      long deliveryTimestamp,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(longKey("rocketmq.message.delivery_timestamp"), deliveryTimestamp),
                equalTo(stringKey("rocketmq.message.type"), DELAY),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "send"),
                equalTo(MESSAGING_OPERATION_TYPE, "send")));
    attributeAssertions.addAll(asList(extraAttributes));

    return span.hasKind(SpanKind.PRODUCER)
        .hasName("send " + topic)
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  private static SpanDataAssert assertReceiveSpan(
      SpanDataAssert span, String topic, String consumerGroup, SpanData linkedSpan) {
    Attributes linkedAttributes =
        linkedSpan == null ? Attributes.empty() : linkedSpan.getAttributes();
    SpanDataAssert result =
        span.hasKind(CLIENT)
            .hasName("receive " + topic)
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(
                equalTo(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "receive"),
                equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                // receiving is a batching operation, so the message id is on the link instead
                equalTo(MESSAGING_MESSAGE_ID, null),
                equalTo(
                    stringKey("rocketmq.message.tag"),
                    linkedAttributes.get(stringKey("rocketmq.message.tag"))),
                equalTo(
                    stringKey("rocketmq.message.group"),
                    linkedAttributes.get(stringKey("rocketmq.message.group"))),
                equalTo(
                    longKey("rocketmq.message.delivery_timestamp"),
                    linkedAttributes.get(longKey("rocketmq.message.delivery_timestamp"))),
                equalTo(
                    stringArrayKey("rocketmq.message.keys"),
                    linkedAttributes.get(stringArrayKey("rocketmq.message.keys"))));
    if (linkedSpan != null) {
      // one link per received message, carrying the attributes of that message
      result.hasLinks(
          LinkData.create(
              asRemote(linkedSpan.getSpanContext()),
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
      SendReceipt sendReceipt) {
    return assertProcessSpan(
        span,
        linkedSpan,
        topic,
        consumerGroup,
        tag,
        keys,
        sendReceipt,
        StatusData.error(),
        equalTo(ERROR_TYPE, "FAILURE"));
  }

  private static SpanDataAssert assertProcessSpan(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    return assertProcessSpan(
        span,
        linkedSpan,
        topic,
        consumerGroup,
        tag,
        keys,
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
      SendReceipt sendReceipt,
      StatusData status,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_OPERATION_TYPE, "process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName("process " + topic)
            .hasStatus(status)
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return result.hasLinks(LinkData.create(asRemote(linkedSpan.getSpanContext())));
  }

  private static SpanDataAssert assertProcessSpanWithFifoMessage(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      String messageGroup,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(stringKey("rocketmq.message.group"), messageGroup),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_OPERATION_TYPE, "process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName("process " + topic)
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return result.hasLinks(LinkData.create(asRemote(linkedSpan.getSpanContext())));
  }

  private static SpanDataAssert assertProcessSpanWithDelayMessage(
      SpanDataAssert span,
      SpanData linkedSpan,
      String topic,
      String consumerGroup,
      String tag,
      String[] keys,
      long deliveryTimestamp,
      SendReceipt sendReceipt,
      AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> attributeAssertions =
        new ArrayList<>(
            asList(
                equalTo(MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                equalTo(stringKey("rocketmq.message.tag"), tag),
                equalTo(stringArrayKey("rocketmq.message.keys"), asList(keys)),
                equalTo(longKey("rocketmq.message.delivery_timestamp"), deliveryTimestamp),
                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
                equalTo(stringKey("messaging.system.name"), "rocketmq"),
                equalTo(MESSAGING_MESSAGE_ID, sendReceipt.getMessageId().toString()),
                equalTo(MESSAGING_DESTINATION_NAME, topic),
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_OPERATION_TYPE, "process")));
    attributeAssertions.addAll(asList(extraAttributes));

    SpanDataAssert result =
        span.hasKind(SpanKind.CONSUMER)
            .hasName("process " + topic)
            .hasStatus(StatusData.unset())
            .hasAttributesSatisfyingExactly(attributeAssertions);
    return result.hasLinks(LinkData.create(asRemote(linkedSpan.getSpanContext())));
  }

  private void assertFailedSendMetrics() {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.sent.messages",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(sum ->
                            sum.hasPointsSatisfying(point ->
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "send"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                                        equalTo(
                                            ERROR_TYPE,
                                            InternalErrorException.class.getName()))))));

    Attributes expectedAttributes =
        Attributes.builder()
            .put(MESSAGING_OPERATION_NAME, "send")
            .put(MESSAGING_OPERATION_TYPE, "send")
            .put(stringKey("messaging.system.name"), "rocketmq")
            .put(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC)
            .put(ERROR_TYPE, InternalErrorException.class.getName())
            .build();

    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.operation.duration",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(point ->
                            "send".equals(point.getAttributes().get(MESSAGING_OPERATION_NAME)))
                        .singleElement()
                        .satisfies(point -> {
                          assertThat(point.getCount()).isEqualTo(1);
                          assertThat(point.getAttributes()).isEqualTo(expectedAttributes);
                        })));
  }

  private void assertFailureMetrics() {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.process.duration",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(histogram ->
                            histogram.hasPointsSatisfying(point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "process"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(ERROR_TYPE, ConsumeResult.FAILURE.name()),
                                        equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC))))));
    // messaging.client.consumed.messages is owned by the receive operation here, which the retry
    // gate cannot hold back, so the redelivery would race this assertion; the counter is asserted
    // in testSendAndConsumeNormalMessage instead
  }

  private void waitForSuccessfulRedelivery(SendReceipt sendReceipt) {
    await()
        .atMost(Duration.ofSeconds(45))
        .untilAsserted(() ->
            assertThat(testing().spans())
                .filteredOn(span ->
                    sendReceipt
                            .getMessageId()
                            .toString()
                            .equals(span.getAttributes().get(MESSAGING_MESSAGE_ID))
                        && span.getStatus().equals(StatusData.unset())
                        && "process".equals(span.getAttributes().get(MESSAGING_OPERATION_NAME)))
                .hasSize(1));
  }

  private void assertMetrics() {
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.sent.messages",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(sum ->
                            sum.hasPointsSatisfying(point ->
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "send"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.operation.duration",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(histogram ->
                            histogram.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                                            equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                            equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                                            equalTo(MESSAGING_OPERATION_TYPE, "send")),
                                // the consumer keeps polling, so the number of recorded
                                // receives is not deterministic
                                point ->
                                    point.hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC),
                                        equalTo(MESSAGING_OPERATION_TYPE, "receive"))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.process.duration",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(histogram ->
                            histogram.hasPointsSatisfying(point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "process"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC))))));
    testing()
        .waitAndAssertMetrics(
            "io.opentelemetry.rocketmq-client-5.0",
            "messaging.client.consumed.messages",
            metrics ->
                metrics.satisfiesExactly(metric ->
                    assertThat(metric)
                        .satisfies(data -> assertThat(data.getLongSumData().getPoints()).hasSize(1))
                        .hasLongSumSatisfying(sum ->
                            sum.hasPointsSatisfying(point ->
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                        equalTo(stringKey("messaging.system.name"), "rocketmq"),
                                        equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                        equalTo(MESSAGING_DESTINATION_NAME, NORMAL_TOPIC))))));
    assertThat(testing().metrics())
        .filteredOn(metric ->
            metric
                .getInstrumentationScopeInfo()
                .getName()
                .equals("io.opentelemetry.rocketmq-client-5.0"))
        .extracting(metric -> metric.getName())
        .containsExactlyInAnyOrder(
            "messaging.client.sent.messages",
            "messaging.client.operation.duration",
            "messaging.process.duration",
            "messaging.client.consumed.messages");
  }
}
