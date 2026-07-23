/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf.NAMESPACE;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** TODO add tests for propagationEnabled flag */
@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractRocketMqClientTest {

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.rocketmq-client.experimental-span-attributes");
  private static final String CONSUMER_GROUP = "consumerGroup";

  private static final Logger logger = LoggerFactory.getLogger(AbstractRocketMqClientTest.class);

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }

  private static void experimentalString(AbstractStringAssert<?> val) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      val.isInstanceOf(String.class);
    }
  }

  private static void experimentalLong(AbstractLongAssert<?> val) {
    if (EXPERIMENTAL_ATTRIBUTES) {
      val.isInstanceOf(Long.class);
    }
  }

  private DefaultMQProducer producer;

  private DefaultMQPushConsumer consumer;

  private String sharedTopic;

  private Message msg;

  private final List<Message> msgs = new ArrayList<>();

  private final TracingMessageListener tracingMessageListener = new TracingMessageListener();

  abstract InstrumentationExtension testing();

  abstract void configureMqProducer(DefaultMQProducer producer);

  abstract void configureMqPushConsumer(DefaultMQPushConsumer consumer);

  @BeforeAll
  void setup() throws MQClientException, InterruptedException {
    sharedTopic = BaseConf.initTopic();
    msg = new Message(sharedTopic, "TagA", "Hello RocketMQ".getBytes(Charset.defaultCharset()));
    Message msg1 =
        new Message(sharedTopic, "TagA", "hello world a".getBytes(Charset.defaultCharset()));
    Message msg2 =
        new Message(sharedTopic, "TagB", "hello world b".getBytes(Charset.defaultCharset()));
    msgs.add(msg1);
    msgs.add(msg2);
    producer = BaseConf.getProducer(BaseConf.nsAddr);
    configureMqProducer(producer);
    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", tracingMessageListener);
    configureMqPushConsumer(consumer);

    // for RocketMQ 5.x wait a bit to ensure that consumer is properly started up
    if (testLatestDeps()) {
      Thread.sleep(30_000);
    }
  }

  @AfterAll
  void cleanup() {
    if (producer != null) {
      producer.shutdown();
    }
    if (consumer != null) {
      consumer.shutdown();
    }
    BaseConf.deleteTempDir();
  }

  @BeforeEach
  void resetTest() {
    tracingMessageListener.reset();
  }

  @Test
  void testRocketmqProduceCallback()
      throws RemotingException,
          InterruptedException,
          MQClientException,
          ExecutionException,
          TimeoutException {
    CompletableFuture<SendResult> result = new CompletableFuture<>();
    producer.send(
        msg,
        new SendCallback() {
          @Override
          public void onSuccess(SendResult sendResult) {
            result.complete(sendResult);
          }

          @Override
          public void onException(Throwable throwable) {
            result.completeExceptionally(throwable);
          }
        });
    SendResult sendResult = result.get(10, SECONDS);
    assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "publish " + sharedTopic
                                    : sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("publish"),
                                operationName("publish"),
                                operationType("publish"),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                equalTo(
                                    stringKey("messaging.rocketmq.send_result"),
                                    experimental("SEND_OK"))),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "process " + sharedTopic
                                    : sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                consumerGroup(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("process"),
                                operationName("process"),
                                operationType("process"),
                                satisfies(
                                    MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_id"),
                                    val -> experimentalLong(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_offset"),
                                    val -> experimentalLong(val))),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  void testRocketmqProduceAndConsume() throws Exception {
    testing()
        .runWithSpan(
            "parent",
            () -> {
              SendResult sendResult = producer.send(msg);
              assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            });
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "publish " + sharedTopic
                                    : sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("publish"),
                                operationName("publish"),
                                operationType("publish"),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                equalTo(
                                    stringKey("messaging.rocketmq.send_result"),
                                    experimental("SEND_OK"))),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "process " + sharedTopic
                                    : sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                consumerGroup(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("process"),
                                operationName("process"),
                                operationType("process"),
                                satisfies(
                                    MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_id"),
                                    val -> experimentalLong(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_offset"),
                                    val -> experimentalLong(val))),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2))));
  }

  @Test
  void testRocketmqProduceAndBatchConsume() throws Exception {
    runBatchConsumeTest(false);
  }

  @Test
  void testBatchConsumeFailure() throws Exception {
    consumer.setMaxReconsumeTimes(0);
    runBatchConsumeTest(true);
  }

  private void runBatchConsumeTest(boolean failConsumption) throws Exception {
    // context propagation doesn't work for batch messages in 5.3.4
    Assumptions.assumeFalse(testLatestDeps());

    consumer.setConsumeMessageBatchMaxSize(2);
    // This test assumes that messages are sent and received as a batch. Occasionally it happens
    // that the messages are not received as a batch, but one by one. This doesn't match what the
    // assertion expects. To reduce flakiness we retry the test when messages weren't received as
    // a batch.
    int maxAttempts = 5;
    for (int i = 0; i < maxAttempts; i++) {
      tracingMessageListener.reset();
      if (failConsumption) {
        tracingMessageListener.failNextMessage();
      }

      testing().runWithSpan("parent", () -> producer.send(msgs));

      tracingMessageListener.waitForMessages();
      if (tracingMessageListener.getLastBatchSize() == 2) {
        break;
      } else if (i < maxAttempts - 1) {
        // if messages weren't received as a batch we get 1 trace instead of 2
        testing().waitForTraces(1);
        Thread.sleep(2_000);
        testing().clearData();
        logger.error("Messages weren't received as batch, retrying");
      }
    }

    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();
    testing()
        .waitAndAssertTraces(
            trace -> {
              SpanContext spanContext = trace.getSpan(1).getSpanContext();
              producerSpanContext.set(
                  SpanContext.createFromRemoteParent(
                      spanContext.getTraceId(),
                      spanContext.getSpanId(),
                      spanContext.getTraceFlags(),
                      spanContext.getTraceState()));

              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(span -> span.hasName("parent").hasKind(SpanKind.INTERNAL));
              assertions.add(
                  span ->
                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "publish " + sharedTopic
                                  : sharedTopic + " publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "rocketmq"),
                              namespace(),
                              equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                              equalTo(
                                  MESSAGING_BATCH_MESSAGE_COUNT,
                                  emitStableMessagingSemconv() ? Long.valueOf(2) : null),
                              oldOperation("publish"),
                              operationName("publish"),
                              operationType("publish"),
                              satisfies(
                                  MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                              satisfies(
                                  stringKey("messaging.rocketmq.broker_address"),
                                  val -> experimentalString(val)),
                              equalTo(
                                  stringKey("messaging.rocketmq.send_result"),
                                  experimental("SEND_OK"))));
              if (emitStableMessagingSemconv()) {
                assertions.add(
                    span ->
                        assertBatchProcessSpan(
                            span,
                            trace.getSpan(1),
                            producerSpanContext.get(),
                            "TagA",
                            failConsumption));
                assertions.add(
                    span ->
                        assertBatchProcessSpan(
                            span,
                            trace.getSpan(1),
                            producerSpanContext.get(),
                            "TagB",
                            failConsumption));
              }
              trace.hasSpansSatisfyingExactly(assertions);
            },
            trace -> {
              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(
                  span ->
                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "receive " + sharedTopic
                                  : "multiple_sources receive")
                          .hasKind(emitStableMessagingSemconv() ? CLIENT : CONSUMER)
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, "rocketmq"),
                              namespace(),
                              consumerGroup(),
                              equalTo(
                                  MESSAGING_DESTINATION_NAME,
                                  emitStableMessagingSemconv() ? sharedTopic : null),
                              equalTo(
                                  MESSAGING_BATCH_MESSAGE_COUNT,
                                  emitStableMessagingSemconv() ? Long.valueOf(2) : null),
                              oldOperation("receive"),
                              operationName("receive"),
                              operationType("receive")));
              if (!emitStableMessagingSemconv()) {
                assertions.add(
                    span ->
                        assertBatchProcessSpan(
                            span,
                            trace.getSpan(0),
                            producerSpanContext.get(),
                            "TagA",
                            failConsumption));
                assertions.add(
                    span ->
                        assertBatchProcessSpan(
                            span,
                            trace.getSpan(0),
                            producerSpanContext.get(),
                            "TagB",
                            failConsumption));
              }
              assertions.add(
                  span ->
                      span.hasName("messageListener")
                          .hasParent(trace.getSpan(0))
                          .hasKind(SpanKind.INTERNAL));
              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  private void assertBatchProcessSpan(
      SpanDataAssert span,
      SpanData parentSpan,
      SpanContext producerSpanContext,
      String messageTag,
      boolean failed) {
    span.hasName(emitStableMessagingSemconv() ? "process " + sharedTopic : sharedTopic + " process")
        .hasKind(SpanKind.CONSUMER)
        .hasParent(parentSpan)
        .hasStatus(failed ? StatusData.error() : StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, "rocketmq"),
            namespace(),
            consumerGroup(),
            equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
            oldOperation("process"),
            operationName("process"),
            operationType("process"),
            equalTo(
                ERROR_TYPE,
                emitStableMessagingSemconv() && failed
                    ? ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT.name()
                    : null),
            satisfies(MESSAGING_MESSAGE_BODY_SIZE, val -> val.isInstanceOf(Long.class)),
            satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
            equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental(messageTag)),
            satisfies(
                stringKey("messaging.rocketmq.broker_address"), val -> experimentalString(val)),
            satisfies(longKey("messaging.rocketmq.queue_id"), val -> experimentalLong(val)),
            satisfies(longKey("messaging.rocketmq.queue_offset"), val -> experimentalLong(val)));
    if (emitStableMessagingSemconv()) {
      span.hasTotalRecordedLinks(0);
    } else {
      span.hasLinksSatisfying(links(producerSpanContext));
    }
  }

  @Test
  void captureMessageHeaderAsSpanAttributes() throws Exception {
    tracingMessageListener.reset();
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Message msg =
                  new Message(
                      sharedTopic, "TagA", "Hello RocketMQ".getBytes(Charset.defaultCharset()));
              msg.putUserProperty("Test-Message-Header", "test");
              msg.putUserProperty("Uncaptured-Header", "password");
              SendResult sendResult = producer.send(msg);
              assertThat(sendResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            });
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "publish " + sharedTopic
                                    : sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("publish"),
                                operationName("publish"),
                                operationType("publish"),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                equalTo(
                                    stringKey("messaging.rocketmq.send_result"),
                                    experimental("SEND_OK")),
                                equalTo(
                                    headerAttributeKey("Test-Message-Header"),
                                    singletonList("test"))),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "process " + sharedTopic
                                    : sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                consumerGroup(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("process"),
                                operationName("process"),
                                operationType("process"),
                                satisfies(
                                    MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_id"),
                                    val -> experimentalLong(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_offset"),
                                    val -> experimentalLong(val)),
                                equalTo(
                                    headerAttributeKey("Test-Message-Header"),
                                    singletonList("test"))),
                    span ->
                        span.hasName("messageListener")
                            .hasParent(trace.getSpan(2))
                            .hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void testRocketmqProduceOneway() throws Exception {
    testing().runWithSpan("parent", () -> producer.sendOneway(msg));
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "publish " + sharedTopic
                                    : sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("publish"),
                                operationName("publish"),
                                operationType("publish"),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val))),
                    span ->
                        span.hasName(
                                emitStableMessagingSemconv()
                                    ? "process " + sharedTopic
                                    : sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                namespace(),
                                consumerGroup(),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                oldOperation("process"),
                                operationName("process"),
                                operationType("process"),
                                satisfies(
                                    MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_id"),
                                    val -> experimentalLong(val)),
                                satisfies(
                                    longKey("messaging.rocketmq.queue_offset"),
                                    val -> experimentalLong(val))),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2))));
  }

  private static Consumer<List<? extends LinkData>> links(SpanContext... spanContexts) {
    return links -> {
      assertThat(links).hasSize(spanContexts.length);
      for (SpanContext spanContext : spanContexts) {
        assertThat(links)
            .anySatisfy(
                link -> {
                  assertThat(link.getSpanContext().getTraceId())
                      .isEqualTo(spanContext.getTraceId());
                  assertThat(link.getSpanContext().getSpanId()).isEqualTo(spanContext.getSpanId());
                  assertThat(link.getSpanContext().getTraceFlags())
                      .isEqualTo(spanContext.getTraceFlags());
                  assertThat(link.getSpanContext().getTraceState())
                      .isEqualTo(spanContext.getTraceState());
                });
      }
    };
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion consumerGroup() {
    return equalTo(
        MESSAGING_CONSUMER_GROUP_NAME, emitStableMessagingSemconv() ? CONSUMER_GROUP : null);
  }

  private static AttributeAssertion namespace() {
    return equalTo(MESSAGING_ROCKETMQ_NAMESPACE, emitStableMessagingSemconv() ? NAMESPACE : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    String operationType = operation.equals("publish") ? "send" : operation;
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationType : null);
  }
}
