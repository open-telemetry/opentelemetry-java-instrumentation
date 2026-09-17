/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.apache.rocketmq.client.consumer.listener.ConsumeReturnType;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqInstrumenterFactoryTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void usesEmptyProducerNamespaceByDefault() {
    SendMessageContext request = mock(SendMessageContext.class);
    when(request.getMessage()).thenReturn(new Message("topic", new byte[0]));
    Instrumenter<SendMessageContext, Void> instrumenter =
        RocketMqInstrumenterFactory.createProducerInstrumenter(
            testing.getOpenTelemetry(), IncludeExclude.builder().build(), false);
    Context parentContext = Context.root();

    assertThat(instrumenter.shouldStart(parentContext, request)).isTrue();
    Context context = instrumenter.start(parentContext, request);
    instrumenter.end(context, request, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "send topic" : "topic publish")
                        .hasKind(PRODUCER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, "rocketmq"),
                            equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_ROCKETMQ_NAMESPACE,
                                emitStableMessagingSemconv() ? "" : null))));
  }

  @Test
  void marksProcessSpanAsErroredWhenConsumeTimedOut() {
    assumeTrue(emitStableMessagingSemconv());

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
