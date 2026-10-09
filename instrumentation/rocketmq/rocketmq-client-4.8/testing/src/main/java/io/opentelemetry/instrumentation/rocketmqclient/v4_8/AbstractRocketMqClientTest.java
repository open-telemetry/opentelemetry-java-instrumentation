/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
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
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
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

  abstract boolean hasBatchCreateSpans();

  abstract boolean isJavaagent();

  DefaultMQProducer producer() {
    return producer;
  }

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
                        span.hasName("send " + sharedTopic)
                            .hasKind(SpanKind.PRODUCER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
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
                        span.hasName("process " + sharedTopic)
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
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
                        span.hasName("send " + sharedTopic)
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
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
                        span.hasName("process " + sharedTopic)
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
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
    runBatchConsumeTest(false, false, false);
  }

  @Test
  void testRocketmqAsyncProduceAndBatchConsume() throws Exception {
    Assumptions.assumeTrue(isJavaagent());
    runBatchConsumeTest(false, false, true);
  }

  @Test
  void testBatchConsumeFailure() throws Exception {
    consumer.setMaxReconsumeTimes(0);
    runBatchConsumeTest(true, false, false);
  }

  @Test
  void testBatchSendPreservesExistingCreationContext() throws Exception {
    Assumptions.assumeTrue(hasBatchCreateSpans());
    runBatchConsumeTest(false, true, false);
  }

  private void runBatchConsumeTest(
      boolean failConsumption, boolean existingCreationContext, boolean async) throws Exception {
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
      for (Message message : msgs) {
        testing()
            .getOpenTelemetry()
            .getPropagators()
            .getTextMapPropagator()
            .fields()
            .forEach(message.getProperties()::remove);
      }
      if (existingCreationContext) {
        msgs.get(0)
            .putUserProperty(
                "traceparent", "00-00000000000000000000000000000001-0000000000000001-01");
      }

      CompletableFuture<SendResult> result = new CompletableFuture<>();
      testing()
          .runWithSpan(
              "parent",
              () -> {
                if (async) {
                  producer.send(
                      msgs,
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
                } else {
                  result.complete(producer.send(msgs));
                }
              });
      assertThat(result.get(10, SECONDS).getSendStatus()).isEqualTo(SendStatus.SEND_OK);

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
    List<SpanContext> messageCreationContexts = new ArrayList<>();
    testing()
        .waitAndAssertTraces(
            trace -> {
              messageCreationContexts.clear();
              boolean hasBatchCreateSpans = hasBatchCreateSpans();
              int size = hasBatchCreateSpans ? (existingCreationContext ? 3 : 4) : 2;
              trace.hasSize(size);
              SpanContext spanContext =
                  spanNamed(trace, size, "send " + sharedTopic).getSpanContext();
              producerSpanContext.set(
                  SpanContext.createFromRemoteParent(
                      spanContext.getTraceId(),
                      spanContext.getSpanId(),
                      spanContext.getTraceFlags(),
                      spanContext.getTraceState()));

              List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
              assertions.add(span -> span.hasName("parent").hasKind(SpanKind.INTERNAL));
              if (hasBatchCreateSpans) {
                if (existingCreationContext) {
                  messageCreationContexts.add(
                      SpanContext.createFromRemoteParent(
                          "00000000000000000000000000000001",
                          "0000000000000001",
                          TraceFlags.getSampled(),
                          TraceState.getDefault()));
                }
                for (SpanData creationSpan : spansNamed(trace, size, "create " + sharedTopic)) {
                  SpanContext creationContext = creationSpan.getSpanContext();
                  messageCreationContexts.add(
                      SpanContext.createFromRemoteParent(
                          creationContext.getTraceId(),
                          creationContext.getSpanId(),
                          creationContext.getTraceFlags(),
                          creationContext.getTraceState()));
                  assertions.add(
                      span ->
                          span.hasName("create " + sharedTopic)
                              .hasKind(SpanKind.PRODUCER)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                  equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                  equalTo(MESSAGING_OPERATION_NAME, "create"),
                                  equalTo(MESSAGING_OPERATION_TYPE, "create"),
                                  satisfies(
                                      MESSAGING_MESSAGE_ID,
                                      val -> val.isInstanceOf(String.class))));
                }
              }
              assertions.add(
                  span -> {
                    span.hasName("send " + sharedTopic)
                        .hasKind(hasBatchCreateSpans ? SpanKind.CLIENT : SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "rocketmq"),
                            equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                            equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, Long.valueOf(2)),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_MESSAGE_ID, null),
                            satisfies(
                                stringKey("messaging.rocketmq.broker_address"),
                                val -> experimentalString(val)),
                            equalTo(
                                stringKey("messaging.rocketmq.send_result"),
                                experimental("SEND_OK")));
                    if (hasBatchCreateSpans) {
                      span.hasLinksSatisfying(
                          links(messageCreationContexts.toArray(new SpanContext[0])));
                    }
                  });
              trace.hasSpansSatisfyingExactlyInAnyOrder(assertions);
            },
            trace -> {
              // a single process span accounts for the whole batch and links to the creation
              // context of every message it accounts for
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    span.hasName("process " + sharedTopic)
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasStatus(failConsumption ? StatusData.error() : StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "rocketmq"),
                            equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                            equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                            equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2L),
                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                            equalTo(MESSAGING_OPERATION_TYPE, "process"),
                            equalTo(
                                ERROR_TYPE,
                                failConsumption ? ConsumeReturnType.FAILED.name() : null));
                    // one link per message the span accounts for
                    span.hasLinksSatisfying(
                        hasBatchCreateSpans()
                            ? links(messageCreationContexts.toArray(new SpanContext[0]))
                            : links(producerSpanContext.get(), producerSpanContext.get()));
                  },
                  span ->
                      span.hasName("messageListener")
                          .hasParent(trace.getSpan(0))
                          .hasKind(SpanKind.INTERNAL));
            });
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
                        span.hasName("send " + sharedTopic)
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
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
                        span.hasName("process " + sharedTopic)
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
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
                        span.hasName("send " + sharedTopic)
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                equalTo(MESSAGING_OPERATION_TYPE, "send"),
                                equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, experimental("TagA")),
                                satisfies(
                                    stringKey("messaging.rocketmq.broker_address"),
                                    val -> experimentalString(val))),
                    span ->
                        span.hasName("process " + sharedTopic)
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(MESSAGING_ROCKETMQ_NAMESPACE, NAMESPACE),
                                equalTo(MESSAGING_CONSUMER_GROUP_NAME, CONSUMER_GROUP),
                                equalTo(MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                equalTo(MESSAGING_MESSAGE_BODY_SIZE, null),
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

  private static SpanData spanNamed(TraceAssert trace, int size, String name) {
    List<SpanData> spans = spansNamed(trace, size, name);
    assertThat(spans).hasSize(1);
    return spans.get(0);
  }

  private static List<SpanData> spansNamed(TraceAssert trace, int size, String name) {
    List<SpanData> spans = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      SpanData span = trace.getSpan(i);
      if (name.equals(span.getName())) {
        spans.add(span);
      }
    }
    return spans;
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
}
