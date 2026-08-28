/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertSendMetrics;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.cloud.stream.messaging.DirectWithAttributesChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class SpringIntegrationMetricsTest {

  private static final String LOWER_CLIENT_INSTRUMENTATION_NAME = "test-lower-messaging-client";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void shouldRecordSendMetricsOnlyForStableSemconv() {
    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("output");
    channel.setAttribute("type", "output");
    channel.addInterceptor(
        SpringIntegrationTelemetry.builder(GlobalOpenTelemetry.get())
            .setProducerSpanEnabled(true)
            .build()
            .createChannelInterceptor());
    channel.subscribe(message -> {});

    channel.send(MessageBuilder.withPayload("test").build());

    if (emitStableMessagingSemconv()) {
      assertSendMetrics(testing, "output");
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void lowerMessagingClientPreventsDuplicateConsumedMetrics() {
    assumeTrue(emitStableMessagingSemconv());

    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("input");
    channel.addInterceptor(
        SpringIntegrationTelemetry.create(GlobalOpenTelemetry.get()).createChannelInterceptor());
    channel.subscribe(message -> {});

    Instrumenter<Message<?>, Void> lowerClientInstrumenter =
        Instrumenter.<Message<?>, Void>builder(
                GlobalOpenTelemetry.get(), LOWER_CLIENT_INSTRUMENTATION_NAME, message -> "receive")
            .addAttributesExtractor(AttributesExtractor.constant(MESSAGING_SYSTEM, "test"))
            .addAttributesExtractor(
                AttributesExtractor.constant(MESSAGING_OPERATION_NAME, "receive"))
            .addAttributesExtractor(
                AttributesExtractor.constant(MESSAGING_OPERATION_TYPE, "receive"))
            .addAttributesExtractor(
                AttributesExtractor.constant(MESSAGING_DESTINATION_NAME, "input"))
            .addOperationMetrics(MessagingConsumerMetrics.getForOperationType())
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

    Message<String> message = MessageBuilder.withPayload("test").build();
    Context context = lowerClientInstrumenter.start(Context.current(), message);
    try (Scope ignored = context.makeCurrent()) {
      channel.send(message);
    }
    lowerClientInstrumenter.end(context, message, null, null);

    testing.waitAndAssertMetrics(
        LOWER_CLIENT_INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "receive"),
                                                equalTo(MESSAGING_SYSTEM, "test"),
                                                equalTo(MESSAGING_DESTINATION_NAME, "input"))))));
    assertNoMetrics(testing);
  }
}
