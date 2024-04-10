/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

abstract class AbstractPulsarClientTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractPulsarClientTest.class);

  private static final DockerImageName DEFAULT_IMAGE_NAME =
      DockerImageName.parse("apachepulsar/pulsar:2.8.0");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static PulsarContainer pulsar;
  static PulsarClient client;
  static PulsarAdmin admin;
  static Producer<String> producer;
  static Consumer<String> consumer;
  static Producer<String> producer2;

  private static String brokerHost;
  private static int brokerPort;

  private static final AttributeKey<String> MESSAGE_TYPE =
      AttributeKey.stringKey("messaging.pulsar.message.type");

  @BeforeAll
  static void beforeAll() throws PulsarClientException {
    pulsar =
        new PulsarContainer(DEFAULT_IMAGE_NAME)
            .withEnv("PULSAR_MEM", "-Xmx128m")
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    pulsar.start();

    brokerHost = pulsar.getHost();
    brokerPort = pulsar.getMappedPort(6650);
    client = PulsarClient.builder().serviceUrl(pulsar.getPulsarBrokerUrl()).build();
    admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.getHttpServiceUrl()).build();
  }

  @AfterEach
  void afterEach() throws PulsarClientException {
    if (producer != null) {
      producer.close();
    }
    if (consumer != null) {
      consumer.close();
    }
    if (producer2 != null) {
      producer2.close();
    }
  }

  @AfterAll
  static void afterAll() throws PulsarClientException {
    if (client != null) {
      client.close();
    }
    if (admin != null) {
      admin.close();
    }
    pulsar.close();
  }

  @Test
  void testConsumeNonPartitionedTopicUsingBatchReceive() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceive";
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

    testing.runWithSpan(
        "receive-parent",
        () -> {
          Messages<String> receivedMsg = consumer.batchReceive();
          consumer.acknowledge(receivedMsg);
        });
    AtomicReference<SpanData> producerSpan = new AtomicReference<>();

    testing.waitAndAssertTraces(
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
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            batchReceiveAttributes(topic, null, false))));
  }

  @Test
  void testConsumeNonPartitionedTopicUsingBatchReceiveAsync() throws Exception {
    String topic =
        "persistent://public/default/testConsumeNonPartitionedTopicCallBatchReceiveAsync";
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

    CompletableFuture<Messages<String>> result =
        testing.runWithSpan(
            "receive-parent",
            () ->
                consumer
                    .batchReceiveAsync()
                    .whenComplete(
                        (messages, throwable) -> {
                          if (messages != null) {
                            testing.runWithSpan(
                                "callback", () -> acknowledgeMessages(consumer, messages));
                          }
                        }));

    assertThat(result.get(1, TimeUnit.MINUTES).size()).isEqualTo(1);

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
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
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(topic + " receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(batchReceiveAttributes(topic, null, false)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(1))));
  }

  static List<AttributeAssertion> sendAttributes(
      String destination, String messageId, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(SERVER_ADDRESS, brokerHost),
                equalTo(SERVER_PORT, brokerPort),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                equalTo(MESSAGING_OPERATION, "publish"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative),
                equalTo(MESSAGE_TYPE, "normal")));

    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  static List<AttributeAssertion> batchReceiveAttributes(
      String destination, String messageId, boolean testHeaders) {
    return receiveAttributes(destination, messageId, testHeaders, true);
  }

  static List<AttributeAssertion> receiveAttributes(
      String destination, String messageId, boolean testHeaders) {
    return receiveAttributes(destination, messageId, testHeaders, false);
  }

  static List<AttributeAssertion> receiveAttributes(
      String destination, String messageId, boolean testHeaders, boolean isBatch) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(SERVER_ADDRESS, brokerHost),
                equalTo(SERVER_PORT, brokerPort),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                equalTo(MESSAGING_OPERATION, "receive"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative)));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    if (isBatch) {
      assertions.add(satisfies(MESSAGING_BATCH_MESSAGE_COUNT, AbstractLongAssert::isPositive));
    }
    return assertions;
  }

  static List<AttributeAssertion> processAttributes(
      String destination, String messageId, boolean testHeaders) {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            Arrays.asList(
                equalTo(MESSAGING_SYSTEM, "pulsar"),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                equalTo(MESSAGING_OPERATION, "process"),
                equalTo(MESSAGING_MESSAGE_ID, messageId),
                satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative)));
    if (testHeaders) {
      assertions.add(
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.test_message_header"),
              Collections.singletonList("test")));
    }
    return assertions;
  }

  static void acknowledgeMessage(Consumer<String> consumer, Message<String> message) {
    try {
      consumer.acknowledge(message);
    } catch (PulsarClientException exception) {
      throw new RuntimeException(exception);
    }
  }

  static void acknowledgeMessages(Consumer<String> consumer, Messages<String> messages) {
    try {
      consumer.acknowledge(messages);
    } catch (PulsarClientException exception) {
      throw new RuntimeException(exception);
    }
  }
}
