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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RocketMqInstrumenterFactoryTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void capturesProducerMessageTagOnlyWhenEnabled(boolean captureExperimentalSpanAttributes) {
    SendMessageContext request = mock(SendMessageContext.class);
    Message message = new Message("topic", new byte[0]);
    message.setTags("tag");
    when(request.getMessage()).thenReturn(message);
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
                            equalTo(stringKey("rocketmq.namespace"), ""),
                            equalTo(
                                stringKey("rocketmq.message.tag"),
                                captureExperimentalSpanAttributes ? "tag" : null))));
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
