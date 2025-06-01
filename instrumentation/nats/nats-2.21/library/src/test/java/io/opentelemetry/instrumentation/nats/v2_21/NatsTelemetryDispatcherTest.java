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

import io.nats.client.MessageHandler;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTelemetryDispatcherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());

  private final MessageHandler handler = msg -> {};

  @Test
  void testDispatcherDefaultHandler() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    connection.createDispatcher(handler).subscribe("sub");
    connection.createDispatcher(handler).subscribe("sub", "queue");

    // when
    testing.runWithSpan(
        "parent",
        () -> testConnection.deliver(NatsMessage.builder().subject("sub").data("x").build()));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> // first dispatcher
                span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span ->
                    span.hasName("sub process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span -> // second dispatcher
                span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span ->
                    span.hasName("sub process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  @Test
  void testDispatcherHandler() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    connection.createDispatcher().subscribe("sub", handler);
    connection.createDispatcher().subscribe("sub", "queue", handler);

    // when
    testing.runWithSpan(
        "parent",
        () -> testConnection.deliver(NatsMessage.builder().subject("sub").data("x").build()));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> // first dispatcher
                span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span ->
                    span.hasName("sub process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span -> // second dispatcher
                span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span ->
                    span.hasName("sub process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  @SuppressWarnings("PreferJavaTimeOverload")
  @Test
  void testDispatcherWithLink() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    connection.createDispatcher(handler).subscribe("sub");
    String linkTraceId = "0af7651916cd43dd8448eb211c80319c";
    Headers headers =
        new Headers(
            new Headers().put("traceparent", "00-" + linkTraceId + "-b7ad6b7169203331-01"), true);

    // when
    testing.runWithSpan(
        "parent",
        () ->
            testConnection.deliver(
                NatsMessage.builder().subject("sub").data("x").headers(headers).build()));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinksSatisfying(
                            links ->
                                assertThat(links)
                                    .singleElement()
                                    .satisfies(
                                        link ->
                                            assertThat(link.getSpanContext().getTraceId())
                                                .isEqualTo(linkTraceId)))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1")),
                span ->
                    span.hasName("sub process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }
}
