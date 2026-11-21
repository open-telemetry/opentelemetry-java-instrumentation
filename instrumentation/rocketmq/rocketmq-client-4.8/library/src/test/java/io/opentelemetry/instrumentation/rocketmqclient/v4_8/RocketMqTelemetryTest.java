/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("deprecation") // asserting incubating messaging attributes and using SDK accessor
class RocketMqTelemetryTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void isolatesProducerConfigurationSnapshots() {
    RocketMqTelemetry telemetry = RocketMqTelemetry.create(testing.getOpenTelemetry());
    DefaultMQProducer first = producer("ns-a:9876");
    DefaultMQProducer second = producer("ns-b:9877");
    SendMessageHook firstHook = instrument(telemetry, first);
    SendMessageHook secondHook = instrument(telemetry, second);
    when(first.getNamesrvAddr()).thenReturn("changed:9999");

    send(firstHook, first, "first");
    send(secondHook, second, "second");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertSend(span, "first", "ns-a", 9876)),
        trace -> trace.hasSpansSatisfyingExactly(span -> assertSend(span, "second", "ns-b", 9877)));
  }

  private static DefaultMQProducer producer(String namesrvAddr) {
    DefaultMQProducer producer = mock(DefaultMQProducer.class);
    when(producer.getNamesrvAddr()).thenReturn(namesrvAddr);
    when(producer.getDefaultMQProducerImpl()).thenReturn(mock(DefaultMQProducerImpl.class));
    return producer;
  }

  private static SendMessageHook instrument(
      RocketMqTelemetry telemetry, DefaultMQProducer producer) {
    telemetry.instrument(producer);
    ArgumentCaptor<SendMessageHook> hook = ArgumentCaptor.forClass(SendMessageHook.class);
    verify(producer.getDefaultMQProducerImpl()).registerSendMessageHook(hook.capture());
    return hook.getValue();
  }

  private static void send(SendMessageHook hook, DefaultMQProducer producer, String topic) {
    SendMessageContext request = new SendMessageContext();
    Message message = new Message(topic, new byte[0]);
    request.setMessage(message);
    request.setProducer(producer.getDefaultMQProducerImpl());
    request.setBrokerAddr("127.0.0.1:10911");
    request.setCommunicationMode(CommunicationMode.ONEWAY);
    hook.sendMessageBefore(request);
    hook.sendMessageAfter(request);
  }

  private static void assertSend(SpanDataAssert span, String topic, String server, int port) {
    span.hasName("send " + topic)
        .hasKind(PRODUCER)
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM_NAME, "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_OPERATION_NAME, "send"),
            equalTo(MESSAGING_OPERATION_TYPE, "send"),
            equalTo(SERVER_ADDRESS, server),
            equalTo(SERVER_PORT, port),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_PORT, 10911));
  }

  @Test
  void isolatesConsumerConfigurationSnapshots() throws UnknownHostException {
    RocketMqTelemetry telemetry = RocketMqTelemetry.create(testing.getOpenTelemetry());
    ClientConfig first = new ClientConfig();
    first.setNamesrvAddr("ns-a:9876");
    ClientConfig second = new ClientConfig();
    second.setNamesrvAddr("ns-b:9877");
    ConsumeMessageHook firstHook = telemetry.createConsumeMessageHook(first);
    ConsumeMessageHook secondHook = telemetry.createConsumeMessageHook(second);
    first.setNamesrvAddr("changed:9999");

    consume(firstHook, "first");
    consume(secondHook, "second");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> assertProcess(span, "first", "ns-a", 9876L)),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> assertProcess(span, "second", "ns-b", 9877L)));
  }

  @Test
  void consumeHookWithoutClientConfigOmitsServerAttributes() throws UnknownHostException {
    ConsumeMessageHook hook =
        RocketMqTelemetry.create(testing.getOpenTelemetry()).createConsumeMessageHook();

    consume(hook, "topic");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertProcess(span, "topic", null, null)));
  }

  private static void consume(ConsumeMessageHook hook, String topic) throws UnknownHostException {
    MessageExt message = new MessageExt();
    message.setTopic(topic);
    MessageAccessor.setProperties(message, new HashMap<>());
    message.setStoreHost(
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10911));
    ConsumeMessageContext request = new ConsumeMessageContext();
    request.setMsgList(singletonList(message));
    request.setConsumerGroup("consumer-group");
    request.setSuccess(true);
    hook.consumeMessageBefore(request);
    hook.consumeMessageAfter(request);
  }

  private static void assertProcess(SpanDataAssert span, String topic, String server, Long port) {
    span.hasName("process " + topic)
        .hasKind(CONSUMER)
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM_NAME, "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            equalTo(MESSAGING_CONSUMER_GROUP_NAME, "consumer-group"),
            equalTo(SERVER_ADDRESS, server),
            equalTo(SERVER_PORT, port),
            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
            equalTo(NETWORK_PEER_PORT, 10911));
  }
}
