/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
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

@SuppressWarnings("deprecation") // using deprecated semconv constants in metric assertions
class RocketMqMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-4.8";
  private static final IncludeExclude NO_HEADERS = IncludeExclude.builder().build();

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("producerCases")
  void recordsProducerMetrics(
      Message message, long expectedCount, Throwable error, String errorType) {
    assumeTrue(emitStableMessagingSemconv());
    SendMessageContext request = mock(SendMessageContext.class);
    when(request.getMessage()).thenReturn(message);
    Instrumenter<SendMessageContext, Void> instrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(), NO_HEADERS, false);

    Context context = instrumenter.start(Context.root(), request);
    instrumenter.end(context, request, null, error);

    assertMessageCount(
        "messaging.client.sent.messages",
        expectedCount,
        "send",
        message.getTopic(),
        null,
        errorType);
    assertDuration(
        "messaging.client.operation.duration", "send", message.getTopic(), null, "send", errorType);
    assertNoDeprecatedMetrics();
  }

  private static Stream<Arguments> producerCases() {
    return Stream.of(
        argumentSet("single success", message("single-success"), 1, null, null),
        argumentSet(
            "single error",
            message("single-error"),
            1,
            new IllegalStateException("test"),
            IllegalStateException.class.getName()),
        argumentSet(
            "batch success",
            MessageBatch.generateFromList(
                asList(message("batch-success"), message("batch-success"))),
            2,
            null,
            null),
        argumentSet(
            "batch error",
            MessageBatch.generateFromList(asList(message("batch-error"), message("batch-error"))),
            2,
            new IllegalStateException("test"),
            IllegalStateException.class.getName()));
  }

  @ParameterizedTest
  @MethodSource("consumerCases")
  void recordsProcessMetrics(
      List<MessageExt> messages, String consumeErrorType, long expectedCount) {
    assumeTrue(emitStableMessagingSemconv());
    ConsumeMessageContext response = new ConsumeMessageContext();
    response.setSuccess(consumeErrorType == null);
    response.setProps(
        singletonMap(
            MixAll.CONSUME_CONTEXT_TYPE,
            consumeErrorType == null ? ConsumeReturnType.SUCCESS.name() : consumeErrorType));
    RocketMqConsumerInstrumenter instrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            testing.getOpenTelemetry(), NO_HEADERS, false);

    RocketMqConsumerInstrumenter.ConsumerContext consumerContext =
        requireNonNull(instrumenter.start(Context.root(), messages, "consumer-group", null));
    instrumenter.end(consumerContext, response);

    String destination = messages.get(0).getTopic();
    assertMessageCount(
        "messaging.client.consumed.messages",
        expectedCount,
        "process",
        destination,
        "consumer-group",
        consumeErrorType);
    assertDuration(
        "messaging.process.duration",
        "process",
        destination,
        "consumer-group",
        null,
        consumeErrorType);
    assertNoDeprecatedMetrics();
  }

  private static Stream<Arguments> consumerCases() {
    return Stream.of(
        argumentSet("single success", singletonList(messageExt("single-success")), null, 1),
        argumentSet(
            "single error",
            singletonList(messageExt("single-error")),
            ConsumeReturnType.EXCEPTION.name(),
            1),
        argumentSet(
            "batch success",
            asList(messageExt("batch-success"), messageExt("batch-success")),
            null,
            2),
        argumentSet(
            "batch error",
            asList(messageExt("batch-error"), messageExt("batch-error")),
            ConsumeReturnType.RETURNNULL.name(),
            2));
  }

  @Test
  void emitsNoMetricsWithoutStableMessagingSemconv() {
    assumeFalse(emitStableMessagingSemconv());
    SendMessageContext sendRequest = mock(SendMessageContext.class);
    when(sendRequest.getMessage()).thenReturn(message("default-send"));
    Instrumenter<SendMessageContext, Void> producerInstrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(), NO_HEADERS, false);
    Context sendContext = producerInstrumenter.start(Context.root(), sendRequest);
    producerInstrumenter.end(sendContext, sendRequest, null, null);

    List<MessageExt> messages =
        asList(messageExt("default-process"), messageExt("default-process"));
    RocketMqConsumerInstrumenter consumerInstrumenter =
        RocketMqInstrumenterFactory.createConsumerInstrumenter(
            testing.getOpenTelemetry(), NO_HEADERS, false);
    RocketMqConsumerInstrumenter.ConsumerContext consumerContext =
        requireNonNull(
            consumerInstrumenter.start(Context.root(), messages, "consumer-group", null));
    consumerInstrumenter.end(consumerContext, new ConsumeMessageContext());

    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && isMessagingMetric(metric.getName()));
  }

  private static Message message(String topic) {
    return new Message(topic, new byte[0]);
  }

  private static MessageExt messageExt(String topic) {
    MessageExt message = new MessageExt();
    message.setTopic(topic);
    message.setMsgId("message-id");
    message.setBody(new byte[0]);
    message.putUserProperty("test", "value");
    return message;
  }

  private static void assertMessageCount(
      String metricName,
      long expectedCount,
      String operationName,
      String destination,
      String consumerGroup,
      String errorType) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(expectedCount)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, operationName),
                                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                equalTo(ERROR_TYPE, errorType),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME, destination))))));
  }

  private static void assertDuration(
      String metricName,
      String operationName,
      String destination,
      String consumerGroup,
      String operationType,
      String errorType) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
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
                                                equalTo(MESSAGING_OPERATION_NAME, operationName),
                                                equalTo(MESSAGING_SYSTEM, "rocketmq"),
                                                equalTo(ERROR_TYPE, errorType),
                                                equalTo(
                                                    MESSAGING_CONSUMER_GROUP_NAME, consumerGroup),
                                                equalTo(MESSAGING_DESTINATION_NAME, destination),
                                                equalTo(
                                                    MESSAGING_OPERATION_TYPE, operationType))))));
  }

  private static void assertNoDeprecatedMetrics() {
    assertThat(testing.metrics())
        .noneMatch(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && (metric.getName().equals("messaging.publish.duration")
                        || metric.getName().equals("messaging.receive.duration")
                        || metric.getName().equals("messaging.receive.messages")));
  }

  private static boolean isMessagingMetric(String metricName) {
    return metricName.equals("messaging.client.operation.duration")
        || metricName.equals("messaging.client.sent.messages")
        || metricName.equals("messaging.client.consumed.messages")
        || metricName.equals("messaging.process.duration")
        || metricName.equals("messaging.publish.duration")
        || metricName.equals("messaging.receive.duration")
        || metricName.equals("messaging.receive.messages");
  }
}
