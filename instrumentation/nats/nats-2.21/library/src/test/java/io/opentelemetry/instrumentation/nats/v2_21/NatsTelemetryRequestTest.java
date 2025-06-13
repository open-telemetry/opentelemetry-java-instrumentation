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
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class NatsTelemetryRequestTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final NatsTelemetry telemetry = NatsTelemetry.create(testing.getOpenTelemetry());

  @Test
  void testRequestTimeout() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent", () -> connection.request("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertPublishSpan();
    assertTimeoutPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestBody() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request("sub", new byte[] {0}, Duration.ofSeconds(1));
        });

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestHeadersBody() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request("sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1));
        });

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  @Test
  void testRequestMessage() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request(message, Duration.ofSeconds(1));
        });

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestMessageHeaders() throws InterruptedException {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request(message, Duration.ofSeconds(1));
        });

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  @Test
  void testRequestFutureTimeout() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent",
        () -> connection.requestWithTimeout("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertPublishSpan();
    assertTimeoutPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestFutureBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request("sub", new byte[] {0});
        });

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestFutureHeadersBody() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request("sub", new Headers(), new byte[] {0});
        });

    // then
    assertPublishSpan();
    assertTraceparentHeader(testConnection);
  }

  @Test
  void testRequestFutureMessage() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request(message);
        });

    // then
    assertPublishSpan();
    assertNoHeaders(testConnection);
  }

  @Test
  void testRequestFutureMessageHeaders() {
    // given
    TestConnection testConnection = new TestConnection();
    OpenTelemetryConnection connection = telemetry.wrap(testConnection);
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing.runWithSpan(
        "parent",
        () -> {
          testConnection.requestResponseMessages.offer(
              NatsMessage.builder().subject("sub").build());
          connection.request(message);
        });

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
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_SYSTEM, "nats"),
                            equalTo(MESSAGING_DESTINATION_NAME, "sub"),
                            equalTo(MESSAGING_MESSAGE_BODY_SIZE, 1),
                            equalTo(AttributeKey.stringKey("messaging.client_id"), "1"))));
  }

  private static void assertTimeoutPublishSpan() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("sub publish")
                        .hasException(new TimeoutException("Timed out waiting for message"))));
  }

  private static void assertNoHeaders(TestConnection connection) {
    Message published = connection.requestedMessages.remove();
    assertNotNull(published);
    assertThat(published.getHeaders()).isNull();
  }

  private static void assertTraceparentHeader(TestConnection connection) {
    Message published = connection.requestedMessages.remove();
    assertNotNull(published);
    assertThat(published.getHeaders().get("traceparent")).isNotEmpty();
  }
}
