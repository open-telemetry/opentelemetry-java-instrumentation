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

import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTelemetrySubscribeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());

  @Test
  void testSubscribeTimeout() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    Subscription subscription = connection.subscribe("sub");

    // when
    testing.runWithSpan("parent", () -> subscription.nextMessage(Duration.ofSeconds(1)));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasException(new TimeoutException("Timed out waiting for message"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 0),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  @Test
  void testSubscribeNoLink() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    Subscription subscription = connection.subscribe("sub");

    // when
    testConnection.deliver(NatsMessage.builder().subject("sub").data("x").build());
    testing.runWithSpan("parent", () -> subscription.nextMessage(Duration.ofSeconds(1)));

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "receive"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  @SuppressWarnings("PreferJavaTimeOverload")
  @Test
  void testSubscribeWithLink() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    Subscription subscription = connection.subscribe("sub");
    String linkTraceId = "0af7651916cd43dd8448eb211c80319c";
    Headers headers =
        new Headers(
            new Headers().put("traceparent", "00-" + linkTraceId + "-b7ad6b7169203331-01"), true);

    // when
    testConnection.deliver(NatsMessage.builder().subject("sub").data("x").headers(headers).build());
    testing.runWithSpan("parent", () -> subscription.nextMessage(1000));

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
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }
}
