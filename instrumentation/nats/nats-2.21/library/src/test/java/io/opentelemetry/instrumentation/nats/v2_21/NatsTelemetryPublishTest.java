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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTelemetryPublishTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());

  @Test
  void testPublishBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", new byte[] {0}));

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testPublishHeadersBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  @Test
  void testPublishReplyToBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan("parent", () -> connection.publish("sub", "rt", new byte[] {0}));

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testPublishReplyToHeadersBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent", () -> connection.publish("sub", "rt", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  @Test
  void testPublishMessage() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testPublishMessageHeaders() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  private static void assertPublishSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName("sub publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  private static void assertNoHeaders(TestConnection connection) {
    Message published = connection.publishedMessages.remove();
    assertNotNull(published);
    assertThat(published.getHeaders()).isNull();
  }

  private static void assertTraceparentHeader(TestConnection connection) {
    Message published = connection.publishedMessages.remove();
    assertNotNull(published);
    assertThat(published.getHeaders().get("traceparent")).isNotEmpty();
  }
}
