/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractRocketMqClientSuppressReceiveSpanTest {
  private static final RocketMqProxyContainer container = new RocketMqProxyContainer();

  protected abstract InstrumentationExtension testing();

  @BeforeAll
  static void setUp() {
    container.start();
  }

  @AfterAll
  static void tearDown() {
    container.close();
  }

  @Test
  void testSendAndConsumeMessage() throws Throwable {
    ClientConfiguration clientConfiguration =
        ClientConfiguration.newBuilder()
            .setEndpoints(container.endpoints)
            .setRequestTimeout(Duration.ofSeconds(10))
            .build();
    // Inner topic of the container.
    String topic = "normal-topic-0";
    ClientServiceProvider provider = ClientServiceProvider.loadService();
    String consumerGroup = "group-normal-topic-0";
    String tag = "tagA";
    FilterExpression filterExpression = new FilterExpression(tag, FilterExpressionType.TAG);
    try (PushConsumer ignored =
        provider
            .newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(consumerGroup)
            .setSubscriptionExpressions(Collections.singletonMap(topic, filterExpression))
            .setMessageListener(
                messageView -> {
                  testing().runWithSpan("child", () -> {});
                  return ConsumeResult.SUCCESS;
                })
            .build()) {
      try (Producer producer =
          provider
              .newProducerBuilder()
              .setClientConfiguration(clientConfiguration)
              .setTopics(topic)
              .build()) {

        String[] keys = new String[] {"yourMessageKey-0", "yourMessageKey-1"};
        byte[] body = "foobar".getBytes(StandardCharsets.UTF_8);
        Message message =
            provider
                .newMessageBuilder()
                .setTopic(topic)
                .setTag(tag)
                .setKeys(keys)
                .setBody(body)
                .build();

        SendReceipt sendReceipt =
            testing()
                .runWithSpan(
                    "parent",
                    (ThrowingSupplier<SendReceipt, Throwable>) () -> producer.send(message));
        testing()
            .waitAndAssertTraces(
                trace ->
                    trace.hasSpansSatisfyingExactly(
                        span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                        span ->
                            span.hasKind(SpanKind.PRODUCER)
                                .hasName(topic + " publish")
                                .hasStatus(StatusData.unset())
                                .hasParent(trace.getSpan(0))
                                .hasAttributesSatisfyingExactly(
                                    equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                                    equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                                    equalTo(
                                        MESSAGING_ROCKETMQ_MESSAGE_TYPE,
                                        MessagingIncubatingAttributes
                                            .MessagingRocketmqMessageTypeIncubatingValues.NORMAL),
                                    equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                    equalTo(
                                        MESSAGING_MESSAGE_ID,
                                        sendReceipt.getMessageId().toString()),
                                    equalTo(MESSAGING_DESTINATION_NAME, topic),
                                    equalTo(MESSAGING_OPERATION, "publish")),
                        span ->
                            span.hasKind(SpanKind.CONSUMER)
                                .hasName(topic + " process")
                                .hasStatus(StatusData.unset())
                                // As the child of send span.
                                .hasParent(trace.getSpan(1))
                                .hasAttributesSatisfyingExactly(
                                    equalTo(
                                        MessagingIncubatingAttributes
                                            .MESSAGING_ROCKETMQ_CLIENT_GROUP,
                                        consumerGroup),
                                    equalTo(MESSAGING_ROCKETMQ_MESSAGE_TAG, tag),
                                    equalTo(MESSAGING_ROCKETMQ_MESSAGE_KEYS, Arrays.asList(keys)),
                                    equalTo(MESSAGING_MESSAGE_BODY_SIZE, (long) body.length),
                                    equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                    equalTo(
                                        MESSAGING_MESSAGE_ID,
                                        sendReceipt.getMessageId().toString()),
                                    equalTo(MESSAGING_DESTINATION_NAME, topic),
                                    equalTo(MESSAGING_OPERATION, "process")),
                        span ->
                            span.hasName("child")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParent(trace.getSpan(2))));
      }
    }
  }
}
