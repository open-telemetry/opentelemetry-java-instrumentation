/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.PROCESS_DURATION;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.add;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryState.enable;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.AbstractSpringIntegrationTracingTest.verifyCorrectSpanWasPropagated;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertNoMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertProcessMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.assertSendMetrics;
import static io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationTestHelper.messagingAttributes;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.atomic.AtomicReference;
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
    AtomicReference<Message<?>> capturedMessage = new AtomicReference<>();
    channel.subscribe(capturedMessage::set);

    runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span -> {
                  span.hasName(emitStableMessagingSemconv() ? "send output" : "output publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(messagingAttributes("send", "output"));
                  verifyCorrectSpanWasPropagated(capturedMessage.get(), trace.getSpan(1));
                }));

    if (emitStableMessagingSemconv()) {
      assertSendMetrics(testing, "output");
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void disabledProducerSpanPropagatesWithoutConsumerTelemetry() {
    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("output");
    channel.setAttribute("type", "output");
    channel.addInterceptor(
        SpringIntegrationTelemetry.create(GlobalOpenTelemetry.get()).createChannelInterceptor());
    AtomicReference<Message<?>> capturedMessage = new AtomicReference<>();
    channel.subscribe(capturedMessage::set);

    runWithSpan("parent", () -> channel.send(MessageBuilder.withPayload("test").build()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName("parent").hasKind(SpanKind.INTERNAL);
                  verifyCorrectSpanWasPropagated(capturedMessage.get(), trace.getSpan(0));
                }));
    assertNoMetrics(testing);
  }

  @Test
  void disabledProducerSpanPreservesOutputToInputParentage() {
    SpringIntegrationTelemetry telemetry =
        SpringIntegrationTelemetry.create(GlobalOpenTelemetry.get());
    DirectWithAttributesChannel output = new DirectWithAttributesChannel();
    output.setBeanName("output");
    output.setAttribute("type", "output");
    output.addInterceptor(telemetry.createChannelInterceptor());
    DirectWithAttributesChannel input = new DirectWithAttributesChannel();
    input.setBeanName("input");
    input.setAttribute("type", "input");
    input.addInterceptor(telemetry.createChannelInterceptor());

    CapturingMessageHandler capturingMessageHandler = new CapturingMessageHandler();
    output.subscribe(input::send);
    input.subscribe(capturingMessageHandler);

    runWithSpan("parent", () -> output.send(MessageBuilder.withPayload("test").build()));

    Message<?> capturedMessage = capturingMessageHandler.join();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span -> {
                  span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                      .hasParent(trace.getSpan(0))
                      .hasKind(SpanKind.CONSUMER)
                      .hasAttributesSatisfyingExactly(messagingAttributes("process", "input"));
                  verifyCorrectSpanWasPropagated(capturedMessage, trace.getSpan(1));
                },
                span -> span.hasName("handler").hasParent(trace.getSpan(1))));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void suppressedNestedProducerSendDoesNotCompleteOuterInvocation() {
    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("output");
    channel.setAttribute("type", "output");
    channel.addInterceptor(
        SpringIntegrationTelemetry.builder(GlobalOpenTelemetry.get())
            .setProducerSpanEnabled(true)
            .build()
            .createChannelInterceptor());
    channel.subscribe(
        message -> {
          if (!message.getHeaders().containsKey("nested")) {
            channel.send(MessageBuilder.fromMessage(message).setHeader("nested", true).build());
            runWithSpan("outerAfterNested", () -> {});
          }
        });

    Context before = Context.current();
    channel.send(MessageBuilder.withPayload("test").build());
    assertThat(Context.current()).isSameAs(before);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "send output" : "output publish")
                        .hasKind(SpanKind.PRODUCER),
                span -> span.hasName("outerAfterNested").hasParent(trace.getSpan(0))));
    if (emitStableMessagingSemconv()) {
      assertSendMetrics(testing, "output");
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void lowerMessagingProcessDoesNotOwnDistinctInputChannelMessage() {
    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("input");
    channel.setAttribute("type", "input");
    channel.addInterceptor(
        SpringIntegrationTelemetry.create(GlobalOpenTelemetry.get()).createChannelInterceptor());
    channel.subscribe(message -> {});

    Instrumenter<Message<?>, Void> lowerClientInstrumenter =
        Instrumenter.<Message<?>, Void>builder(
                GlobalOpenTelemetry.get(), LOWER_CLIENT_INSTRUMENTATION_NAME, message -> "process")
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

    Message<String> message = MessageBuilder.withPayload("test").build();
    Context context = lowerClientInstrumenter.start(Context.current(), message);
    context = SpanKey.CONSUMER_PROCESS.storeInContext(context, Span.fromContext(context));
    try (Scope ignored = context.makeCurrent()) {
      channel.send(message);
    }
    lowerClientInstrumenter.end(context, message, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("process").hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }

  @Test
  void trackedLowerMessagingProcessDoesNotSuppressDistinctInputChannelMetrics() {
    DirectWithAttributesChannel channel = new DirectWithAttributesChannel();
    channel.setBeanName("input");
    channel.setAttribute("type", "input");
    channel.addInterceptor(
        SpringIntegrationTelemetry.create(GlobalOpenTelemetry.get()).createChannelInterceptor());
    channel.subscribe(message -> {});

    Instrumenter<Message<?>, Void> lowerClientInstrumenter =
        Instrumenter.<Message<?>, Void>builder(
                GlobalOpenTelemetry.get(), LOWER_CLIENT_INSTRUMENTATION_NAME, message -> "process")
            .buildInstrumenter(SpanKindExtractor.alwaysConsumer());

    Message<String> message = MessageBuilder.withPayload("test").build();
    Context context = lowerClientInstrumenter.start(Context.current(), message);
    context = SpanKey.CONSUMER_PROCESS.storeInContext(context, Span.fromContext(context));
    context = add(enable(context), PROCESS, PROCESS_DURATION);
    try (Scope ignored = context.makeCurrent()) {
      channel.send(message);
    }
    lowerClientInstrumenter.end(context, message, null, null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("process").hasKind(SpanKind.CONSUMER),
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "process input" : "input process")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CONSUMER)));
    if (emitStableMessagingSemconv()) {
      assertProcessMetrics(testing, "input", false);
    } else {
      assertNoMetrics(testing);
    }
  }
}
