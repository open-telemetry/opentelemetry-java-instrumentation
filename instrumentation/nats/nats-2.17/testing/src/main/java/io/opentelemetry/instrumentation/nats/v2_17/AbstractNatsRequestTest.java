/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.assertTraceparentHeader;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.messagingAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsRequestTest extends AbstractNatsTest {

  private int clientId;
  private Subscription subscription;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
    subscription = connection.subscribe("sub");
  }

  @AfterEach
  void afterEach() throws InterruptedException {
    subscription.drain(Duration.ofSeconds(10));
  }

  @Test
  void testRequestTimeout() throws InterruptedException {
    // when
    Message message =
        testing()
            .runWithSpan(
                "parent", () -> connection.request("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertThat(message).isNull();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("publish", "sub", clientId))));
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");

    // when
    Message message =
        testing()
            .runWithSpan(
                "parent", () -> connection.request("sub", new byte[] {0}, Duration.ofSeconds(10)));
    connection.closeDispatcher(dispatcher);

    // then
    assertThat(message).isNotNull();
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestHeadersBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");

    // when
    Message message =
        testing()
            .runWithSpan(
                "parent",
                () ->
                    connection.request(
                        "sub", new Headers(), new byte[] {0}, Duration.ofSeconds(10)));
    connection.closeDispatcher(dispatcher);

    // then
    assertThat(message).isNotNull();
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestMessage() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    Message response =
        testing().runWithSpan("parent", () -> connection.request(message, Duration.ofSeconds(10)));
    connection.closeDispatcher(dispatcher);

    // then
    assertThat(response).isNotNull();
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestMessageHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    Message response =
        testing().runWithSpan("parent", () -> connection.request(message, Duration.ofSeconds(10)));
    connection.closeDispatcher(dispatcher);

    // then
    assertThat(response).isNotNull();
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestFutureBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");

    // when
    CompletableFuture<Message> message =
        testing()
            .runWithSpan("parent", () -> connection.request("sub", new byte[] {0}))
            .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
    assertThat(message).isCompletedWithValueMatching(Objects::nonNull);
  }

  @Test
  void testRequestFutureHeadersBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");

    // when
    CompletableFuture<Message> message =
        testing()
            .runWithSpan("parent", () -> connection.request("sub", new Headers(), new byte[] {0}))
            .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
    assertThat(message).isCompletedWithValueMatching(Objects::nonNull);
  }

  @Test
  void testRequestFutureMessage() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    CompletableFuture<Message> response =
        testing()
            .runWithSpan("parent", () -> connection.request(message))
            .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
    assertThat(response).isCompletedWithValueMatching(Objects::nonNull);
  }

  @Test
  void testRequestFutureMessageHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection
            .createDispatcher(m -> connection.publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    CompletableFuture<Message> response =
        testing()
            .runWithSpan("parent", () -> connection.request(message))
            .whenComplete((m, e) -> connection.closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
    assertThat(response).isCompletedWithValueMatching(Objects::nonNull);
  }

  @Test
  void testRequestTimeoutFutureBody() throws InterruptedException {
    // when
    CompletableFuture<Message> message =
        testing()
            .runWithSpan(
                "parent",
                () -> connection.requestWithTimeout("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
    assertThat(message).isCompletedExceptionally();
  }

  @Test
  void testRequestTimeoutFutureHeadersBody() throws InterruptedException {
    // when
    CompletableFuture<Message> message =
        testing()
            .runWithSpan(
                "parent",
                () ->
                    connection.requestWithTimeout(
                        "sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
    assertThat(message).isCompletedExceptionally();
  }

  @Test
  void testRequestTimeoutFutureMessage() throws InterruptedException {
    // given
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    CompletableFuture<Message> response =
        testing()
            .runWithSpan(
                "parent", () -> connection.requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
    assertThat(response).isCompletedExceptionally();
  }

  @Test
  void testRequestTimeoutFutureMessageHeaders() throws InterruptedException {
    // given
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    CompletableFuture<Message> response =
        testing()
            .runWithSpan(
                "parent", () -> connection.requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
    assertThat(response).isCompletedExceptionally();
  }

  private void assertPublishReceiveSpansSameTrace() {
    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> asserts =
                  new ArrayList<>(
                      asList(
                          // publisher: parent + publish
                          span -> span.hasName("parent").hasNoParent(),
                          span ->
                              span.hasName("sub publish")
                                  .hasKind(SpanKind.PRODUCER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      messagingAttributes("publish", "sub", clientId)),
                          // subscriber: process + publish(response)
                          span ->
                              span.hasName("sub process")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(1)),
                          span ->
                              span.hasName("(temporary) publish")
                                  .hasKind(SpanKind.PRODUCER)
                                  .hasParent(trace.getSpan(2)),
                          // publisher: process
                          span ->
                              span.hasName("(temporary) process")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(3))
                                  .hasAttributesSatisfyingExactly(
                                      messagingAttributes(
                                          "process",
                                          "(temporary)",
                                          clientId,
                                          equalTo(MESSAGING_DESTINATION_TEMPORARY, true)))));

              trace.hasSpansSatisfyingExactly(asserts);
            });
  }

  private void assertCancellationPublishSpan() {
    assertExceptionPublishSpan(
        new CancellationException(
            "Future cancelled, response not registered in time, check connection status."));
  }

  private void assertExceptionPublishSpan(Throwable exception) {
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasException(exception)
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("publish", "sub", clientId))));
  }
}
