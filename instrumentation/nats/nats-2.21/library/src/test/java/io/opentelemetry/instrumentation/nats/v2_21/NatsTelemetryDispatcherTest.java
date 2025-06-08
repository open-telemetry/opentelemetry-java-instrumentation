/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTelemetryDispatcherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());

  private final MessageHandler handler = msg -> {};

  @Test
  void testSubscribeDefaultHandler() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    Dispatcher d1 = connection.createDispatcher(handler);
    d1.subscribe("sub1");
    d1.subscribe("sub2", "queue");
    connection.createDispatcher(handler).subscribe("sub3");

    // when
    testConnection.deliver(NatsMessage.builder().subject("sub1").data("x").build());
    testConnection.deliver(NatsMessage.builder().subject("sub2").data("x").build());
    testConnection.deliver(NatsMessage.builder().subject("sub3").data("x").build());

    // then
    testing.waitAndAssertTraces(
        trace -> assertReceiveProcessSpans(trace, "sub1"),
        trace -> assertReceiveProcessSpans(trace, "sub2"),
        trace -> assertReceiveProcessSpans(trace, "sub3"));

    assertThatNoException()
        .isThrownBy(
            () -> {
              d1.unsubscribe("sub1");
              d1.unsubscribe("sub2", 1);
            });
  }

  @Test
  void testSubscribeCustomHandler() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    Dispatcher dispatcher = connection.createDispatcher();
    Subscription s1 = dispatcher.subscribe("sub1", handler);
    Subscription s2 = dispatcher.subscribe("sub2", "queue", handler);
    Subscription s3 = connection.createDispatcher(handler).subscribe("sub3", handler);

    // when
    testConnection.deliver(NatsMessage.builder().subject("sub1").data("x").build());
    testConnection.deliver(NatsMessage.builder().subject("sub2").data("x").build());
    testConnection.deliver(NatsMessage.builder().subject("sub3").data("x").build());

    // then
    testing.waitAndAssertTraces(
        trace -> assertReceiveProcessSpans(trace, "sub1"),
        trace -> assertReceiveProcessSpans(trace, "sub2"),
        trace -> assertReceiveProcessSpans(trace, "sub3"));

    // and
    assertThatNoException()
        .isThrownBy(
            () -> {
              s1.unsubscribe();
              dispatcher.unsubscribe(s2);
              s3.unsubscribe(1);
            });
  }

  @Test
  void testSpanLink() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    connection.createDispatcher(handler).subscribe("sub");

    String linkTraceId = "0af7651916cd43dd8448eb211c80319c";
    Headers headers =
        new Headers(
            new Headers().put("traceparent", "00-" + linkTraceId + "-b7ad6b7169203331-01"), true);

    // when
    testConnection.deliver(NatsMessage.builder().subject("sub").data("x").headers(headers).build());

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("sub receive")
                        .hasNoParent()
                        .hasLinksSatisfying(
                            links ->
                                assertThat(links)
                                    .singleElement()
                                    .satisfies(
                                        link ->
                                            assertThat(link.getSpanContext().getTraceId())
                                                .isEqualTo(linkTraceId))),
                span -> span.hasName("sub process").hasParent(trace.getSpan(0))));
  }

  private static void assertReceiveProcessSpans(TraceAssert trace, String subject) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName(subject + " receive")
                .hasKind(SpanKind.CONSUMER)
                .hasNoParent()
                .hasAttributesSatisfyingExactly(
                    equalTo(MESSAGING_OPERATION, "receive"),
                    equalTo(MESSAGING_SYSTEM, "nats"),
                    equalTo(MESSAGING_DESTINATION_NAME, subject),
                    equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                    equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
        span ->
            span.hasName(subject + " process")
                .hasKind(SpanKind.INTERNAL)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(MESSAGING_OPERATION, "process"),
                    equalTo(MESSAGING_SYSTEM, "nats"),
                    equalTo(MESSAGING_DESTINATION_NAME, subject),
                    equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                    equalTo(AttributeKey.stringKey("messaging.client_id"), "1")));
  }
}
