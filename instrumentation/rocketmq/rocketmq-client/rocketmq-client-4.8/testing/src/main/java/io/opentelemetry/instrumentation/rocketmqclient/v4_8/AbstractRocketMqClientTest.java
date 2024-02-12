/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.base.BaseConf;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** TODO add tests for propagationEnabled flag */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractRocketMqClientTest {

  abstract InstrumentationExtension testing();

  DefaultMQProducer producer;

  DefaultMQPushConsumer consumer;

  String sharedTopic;

  Message msg;

  List<Message> msgs = new ArrayList<>();

  TracingMessageListener tracingMessageListener = new TracingMessageListener();

  abstract void configureMqProducer(DefaultMQProducer producer);

  abstract void configureMqPushConsumer(DefaultMQPushConsumer consumer);

  @BeforeAll
  void setupSpec() throws MQClientException, InterruptedException {
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
    if (Boolean.getBoolean("testLatestDeps")) {
      Thread.sleep(30_000);
    }
  }

  @AfterAll
  void cleanupSpec() {
    if (producer != null) {
      producer.shutdown();
    }
    if (consumer != null) {
      consumer.shutdown();
    }
    BaseConf.deleteTempDir();
  }

  @BeforeEach
  void setup() {
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
    SendResult sendResult = result.get(10, TimeUnit.SECONDS);
    assertEquals(SendStatus.SEND_OK, sendResult.getSendStatus(), "Send status should be SEND_OK");
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(
                                    AttributeKey.stringKey("messaging.rocketmq.send_result"),
                                    "SEND_OK")),
                    span ->
                        span.hasName(sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_id"),
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_offset"),
                                    val -> val.isInstanceOf(Long.class))),
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
              assertEquals(
                  SendStatus.SEND_OK, sendResult.getSendStatus(), "Send status should be SEND_OK");
            });
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(
                                    AttributeKey.stringKey("messaging.rocketmq.send_result"),
                                    "SEND_OK")),
                    span ->
                        span.hasName(sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_id"),
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_offset"),
                                    val -> val.isInstanceOf(Long.class))),
                    span ->
                        span.hasName("messageListener")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2))));
  }

  @Test
  void testRocketmqProduceAndBatchConsume() throws Exception {
    consumer.setConsumeMessageBatchMaxSize(2);
    // This test assumes that messages are sent and received as a batch. Occasionally it happens
    // that the messages are not received as a batch, but one by one. This doesn't match what the
    // assertion expects. To reduce flakiness we retry the test when messages weren't received as
    // a batch.
    int maxAttempts = 5;
    for (int i = 0; i < maxAttempts; i++) {
      tracingMessageListener.reset();
      testing().runWithSpan("parent", () -> producer.send(msgs));
      tracingMessageListener.waitForMessages();
      if (tracingMessageListener.getLastBatchSize() == 2) {
        break;
      } else if (i < maxAttempts) {
        // if messages weren't received as a batch we get 1 trace instead of 2
        testing().waitForTraces(1);
        testing().clearData();
        System.err.println("Messages weren't received as batch, retrying");
      }
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                      span ->
                          span.hasName(sharedTopic + " publish")
                              .hasKind(SpanKind.PRODUCER)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(
                                      SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                  equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                                  satisfies(
                                      SemanticAttributes.MESSAGING_MESSAGE_ID,
                                      val -> val.isInstanceOf(String.class)),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                      val -> val.isInstanceOf(String.class)),
                                  equalTo(
                                      AttributeKey.stringKey("messaging.rocketmq.send_result"),
                                      "SEND_OK"))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("multiple_sources receive")
                              .hasKind(SpanKind.CONSUMER)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive")),
                      span ->
                          span.hasName(sharedTopic + " process")
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(0))
                              .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(
                                      SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                  equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                  satisfies(
                                      SemanticAttributes.MESSAGING_MESSAGE_ID,
                                      val -> val.isInstanceOf(String.class)),
                                  equalTo(
                                      SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                      val -> val.isInstanceOf(Long.class)),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.queue_offset"),
                                      val -> val.isInstanceOf(Long.class))),
                      span ->
                          span.hasName(sharedTopic + " process")
                              .hasKind(SpanKind.CONSUMER)
                              .hasParent(trace.getSpan(0))
                              .hasLinks(LinkData.create(trace.getSpan(1).getSpanContext()))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                  equalTo(
                                      SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                  equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                  satisfies(
                                      SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                      val -> val.isInstanceOf(Long.class)),
                                  satisfies(
                                      SemanticAttributes.MESSAGING_MESSAGE_ID,
                                      val -> val.isInstanceOf(String.class)),
                                  equalTo(
                                      SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                      val -> val.isInstanceOf(String.class)),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.queue_id"),
                                      val -> val.isInstanceOf(Long.class)),
                                  satisfies(
                                      AttributeKey.stringKey("messaging.rocketmq.queue_offset"),
                                      val -> val.isInstanceOf(Long.class))),
                      span -> span.hasName("messageListener").hasKind(SpanKind.INTERNAL)));
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
              msg.putUserProperty("test-message-header", "test");
              SendResult sendResult = producer.send(msg);
              assertEquals(
                  SendStatus.SEND_OK, sendResult.getSendStatus(), "Send status should be SEND_OK");
            });
    // waiting longer than assertTraces below does on its own because of CI flakiness
    tracingMessageListener.waitForMessages();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(sharedTopic + " publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(
                                    AttributeKey.stringKey("messaging.rocketmq.send_result"),
                                    "SEND_OK"),
                                equalTo(
                                    AttributeKey.stringArrayKey(
                                        "messaging.header.test_message_header"),
                                    singletonList("test"))),
                    span ->
                        span.hasName(sharedTopic + " process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.MESSAGING_SYSTEM, "rocketmq"),
                                equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, sharedTopic),
                                equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    SemanticAttributes.MESSAGING_MESSAGE_ID,
                                    val -> val.isInstanceOf(String.class)),
                                equalTo(SemanticAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG, "TagA"),
                                satisfies(
                                    AttributeKey.stringKey("messaging.rocketmq.broker_address"),
                                    val -> val.isInstanceOf(String.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_id"),
                                    val -> val.isInstanceOf(Long.class)),
                                satisfies(
                                    AttributeKey.longKey("messaging.rocketmq.queue_offset"),
                                    val -> val.isInstanceOf(Long.class)),
                                equalTo(
                                    AttributeKey.stringArrayKey(
                                        "messaging.header.test_message_header"),
                                    singletonList("test"))),
                    span ->
                        span.hasName("messageListener")
                            .hasParent(trace.getSpan(2))
                            .hasKind(SpanKind.INTERNAL)));
  }
}
