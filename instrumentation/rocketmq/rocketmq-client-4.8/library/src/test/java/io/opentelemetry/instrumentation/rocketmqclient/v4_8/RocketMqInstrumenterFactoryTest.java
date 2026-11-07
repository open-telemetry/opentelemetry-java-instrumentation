/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv constants in assertions
class RocketMqInstrumenterFactoryTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void capturesProducerServerAndExperimentalMessageTag(boolean captureExperimentalSpanAttributes) {
    SendMessageContext request = mock(SendMessageContext.class);
    Message message = new Message("topic", new byte[0]);
    message.setTags("tag");
    when(request.getMessage()).thenReturn(message);
    when(request.getBrokerAddr()).thenReturn("broker.example:10911");
    Instrumenter<SendMessageContext, Void> instrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(),
            IncludeExclude.builder().build(),
            captureExperimentalSpanAttributes);
    Context parentContext = Context.root();

    assertThat(instrumenter.shouldStart(parentContext, request)).isTrue();
    Context context = instrumenter.start(parentContext, request);
    instrumenter.end(context, request, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send topic")
                        .hasKind(PRODUCER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("messaging.system.name"), "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(SERVER_ADDRESS, "broker.example"),
                            equalTo(SERVER_PORT, 10911),
                            equalTo(
                                stringKey("rocketmq.message.tag"),
                                captureExperimentalSpanAttributes ? "tag" : null))));
  }

  @ParameterizedTest
  @MethodSource("producerCases")
  void capturesProducerServer(String brokerAddress, String expectedAddress, Long expectedPort) {
    SendMessageContext request = new SendMessageContext();
    request.setMessage(new Message("topic", new byte[0]));
    request.setBrokerAddr(brokerAddress);
    Instrumenter<SendMessageContext, Void> instrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);

    Context context = instrumenter.start(Context.root(), request);
    ReadableSpan startedSpan = (ReadableSpan) Span.fromContext(context);
    assertThat(startedSpan.toSpanData().getAttributes().get(SERVER_ADDRESS))
        .isEqualTo(expectedAddress);
    assertThat(startedSpan.toSpanData().getAttributes().get(SERVER_PORT)).isEqualTo(expectedPort);
    instrumenter.end(context, request, null, null);

    assertSendSpan(expectedAddress, expectedPort, null);
  }

  private static Stream<Arguments> producerCases() {
    return Stream.of(
        argumentSet("IPv4", "127.0.0.1:10911", "127.0.0.1", 10911L),
        argumentSet("hostname", "broker.example:10911", "broker.example", 10911L),
        argumentSet("bracketed IPv6", "[2001:db8::1]:10911", "2001:db8::1", 10911L),
        argumentSet("unbracketed IPv6", "2001:db8::1:10911", "2001:db8::1", 10911L),
        argumentSet("alternate port", "127.0.0.1:10909", "127.0.0.1", 10909L),
        argumentSet("unavailable", null, null, null));
  }

  @ParameterizedTest
  @MethodSource("batchProducerCases")
  void capturesBatchServerAtCompletion(
      String finalAddress, String expectedAddress, Long expectedPort) {
    SendMessageContext request = new SendMessageContext();
    request.setMessage(
        MessageBatch.generateFromList(
            asList(new Message("topic", new byte[0]), new Message("topic", new byte[0]))));
    Instrumenter<SendMessageContext, Void> instrumenter =
        RocketMqInstrumenterFactory.createBatchProducerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);

    Context context = instrumenter.start(Context.root(), request);
    ReadableSpan startedSpan = (ReadableSpan) Span.fromContext(context);
    assertThat(startedSpan.toSpanData().getAttributes().get(SERVER_ADDRESS)).isNull();
    assertThat(startedSpan.toSpanData().getAttributes().get(SERVER_PORT)).isNull();

    // Both batch implementations copy this value from the send hook after span creation.
    request.setBrokerAddr(finalAddress);
    instrumenter.end(context, request, null, null);

    assertSendSpan(expectedAddress, expectedPort, 2L);
  }

  private static Stream<Arguments> batchProducerCases() {
    return Stream.of(
        argumentSet("address supplied by hook", "127.0.0.1:10911", "127.0.0.1", 10911L),
        argumentSet("routing did not supply an address", null, null, null));
  }

  private static void assertSendSpan(String expectedAddress, Long expectedPort, Long batchCount) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send topic")
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM_NAME, "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchCount),
                            equalTo(SERVER_ADDRESS, expectedAddress),
                            equalTo(SERVER_PORT, expectedPort))));
  }

  @ParameterizedTest
  @MethodSource("consumerCases")
  void capturesConsumerServer(
      List<MessageExt> messages, String expectedAddress, Long expectedPort) {
    RocketMqConsumerInstrumenter instrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);
    RocketMqConsumerInstrumenter.ConsumerContext context =
        requireNonNull(instrumenter.start(Context.root(), messages, "consumer-group", null));
    ConsumeMessageContext response = new ConsumeMessageContext();
    response.setSuccess(true);
    instrumenter.end(context, response);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process topic")
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM_NAME, "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                            equalTo(MESSAGING_OPERATION_TYPE, "process"),
                            equalTo(MESSAGING_CONSUMER_GROUP_NAME, "consumer-group"),
                            equalTo(
                                MESSAGING_BATCH_MESSAGE_COUNT,
                                messages.size() > 1 ? (long) messages.size() : null),
                            equalTo(SERVER_ADDRESS, expectedAddress),
                            equalTo(SERVER_PORT, expectedPort))));
  }

  private static Stream<Arguments> consumerCases() throws UnknownHostException {
    InetSocketAddress ipv4 =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 10911);
    InetSocketAddress otherAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 2}), 10911);
    InetSocketAddress otherPort = new InetSocketAddress(ipv4.getAddress(), 10912);
    InetSocketAddress ipv6 =
        new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
            10911);
    return Stream.of(
        argumentSet("single", singletonList(message(ipv4)), "127.0.0.1", 10911L),
        argumentSet("IPv6", singletonList(message(ipv6)), "0:0:0:0:0:0:0:1", 10911L),
        argumentSet(
            "same endpoint",
            asList(message(ipv4), message(new InetSocketAddress(ipv4.getAddress(), 10911))),
            "127.0.0.1",
            10911L),
        argumentSet(
            "different addresses", asList(message(ipv4), message(otherAddress)), null, null),
        argumentSet("different ports", asList(message(ipv4), message(otherPort)), null, null),
        argumentSet("missing address", singletonList(message(null)), null, null),
        argumentSet("one missing address", asList(message(ipv4), message(null)), null, null));
  }

  private static MessageExt message(InetSocketAddress address) {
    MessageExt message = new MessageExt();
    message.setTopic("topic");
    message.putUserProperty("test", "value");
    message.setStoreHost(address);
    return message;
  }

  @Test
  void marksProcessSpanAsErroredWhenConsumeTimedOut() {
    MessageExt message = new MessageExt();
    message.setTopic("topic");
    message.putUserProperty("test-header", "test-value");
    // rocketmq reports TIME_OUT for a listener that exceeded the configured consume timeout even
    // when that listener eventually returned a success status
    ConsumeMessageContext response = new ConsumeMessageContext();
    response.setSuccess(true);
    response.setProps(singletonMap(MixAll.CONSUME_CONTEXT_TYPE, ConsumeReturnType.TIME_OUT.name()));
    RocketMqConsumerInstrumenter instrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);

    RocketMqConsumerInstrumenter.ConsumerContext consumerContext =
        requireNonNull(
            instrumenter.start(Context.root(), singletonList(message), "consumer-group", null));
    instrumenter.end(consumerContext, response);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process topic")
                        .hasKind(CONSUMER)
                        .hasStatus(StatusData.error())
                        .hasAttribute(ERROR_TYPE, ConsumeReturnType.TIME_OUT.name())));
  }
}
