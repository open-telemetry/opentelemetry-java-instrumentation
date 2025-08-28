/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.assertNoHeaders;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.assertTraceparentHeader;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.messagingAttributes;
import static java.util.Arrays.asList;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsInstrumentationRequestTest {

  protected abstract InstrumentationExtension testing();

  protected abstract Connection connection();

  protected abstract boolean isInboxMonitored();

  private int clientId;
  private Subscription subscription;

  @BeforeEach
  void beforeEach() {
    clientId = connection().getServerInfo().getClientId();
    subscription = connection().subscribe("sub");
  }

  @AfterEach
  void afterEach() throws InterruptedException {
    subscription.drain(Duration.ofSeconds(1));
  }

  @Test
  void testRequestTimeout() throws InterruptedException {
    // when
    testing()
        .runWithSpan(
            "parent", () -> connection().request("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertTimeoutPublishSpan();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");

    // when
    testing()
        .runWithSpan(
            "parent", () -> connection().request("sub", new byte[] {0}, Duration.ofSeconds(1)));
    connection().closeDispatcher(dispatcher);

    // then
    assertPublishReceiveSpans();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestHeadersBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");

    // when
    testing()
        .runWithSpan(
            "parent",
            () ->
                connection().request("sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1)));
    connection().closeDispatcher(dispatcher);

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestMessage() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing().runWithSpan("parent", () -> connection().request(message, Duration.ofSeconds(1)));
    connection().closeDispatcher(dispatcher);

    // then
    assertPublishReceiveSpans();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestMessageHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing().runWithSpan("parent", () -> connection().request(message, Duration.ofSeconds(1)));
    connection().closeDispatcher(dispatcher);

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestFutureBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");

    // when
    testing()
        .runWithSpan("parent", () -> connection().request("sub", new byte[] {0}))
        .whenComplete((m, e) -> connection().closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpans();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestFutureHeadersBody() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");

    // when
    testing()
        .runWithSpan("parent", () -> connection().request("sub", new Headers(), new byte[] {0}))
        .whenComplete((m, e) -> connection().closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestFutureMessage() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), m.getData()))
            .subscribe("sub");
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing()
        .runWithSpan("parent", () -> connection().request(message))
        .whenComplete((m, e) -> connection().closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpans();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestFutureMessageHeaders() throws InterruptedException {
    // given
    Dispatcher dispatcher =
        connection()
            .createDispatcher(m -> connection().publish(m.getReplyTo(), new Headers(), m.getData()))
            .subscribe("sub");
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing()
        .runWithSpan("parent", () -> connection().request(message))
        .whenComplete((m, e) -> connection().closeDispatcher(dispatcher));

    // then
    assertPublishReceiveSpansSameTrace();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestTimeoutFutureBody() throws InterruptedException {
    // when
    testing()
        .runWithSpan(
            "parent",
            () -> connection().requestWithTimeout("sub", new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestTimeoutFutureHeadersBody() throws InterruptedException {
    // when
    testing()
        .runWithSpan(
            "parent",
            () ->
                connection()
                    .requestWithTimeout(
                        "sub", new Headers(), new byte[] {0}, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testRequestTimeoutFutureMessage() throws InterruptedException {
    // given
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing()
        .runWithSpan(
            "parent", () -> connection().requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertNoHeaders(subscription);
  }

  @Test
  void testRequestTimeoutFutureMessageHeaders() throws InterruptedException {
    // given
    NatsMessage message =
        NatsMessage.builder().subject("sub").headers(new Headers()).data("x").build();

    // when
    testing()
        .runWithSpan(
            "parent", () -> connection().requestWithTimeout(message, Duration.ofSeconds(1)));

    // then
    assertCancellationPublishSpan();
    assertTraceparentHeader(subscription);
  }

  private void assertPublishReceiveSpans() {
    List<Consumer<TraceAssert>> asserts =
        new ArrayList<>(
            asList(
                // publisher: parent + publish
                trace ->
                    trace.hasSpansSatisfyingExactly(
                        span -> span.hasName("parent").hasNoParent(),
                        span ->
                            span.hasName("sub publish")
                                .hasKind(SpanKind.PRODUCER)
                                .hasParent(trace.getSpan(0))
                                .hasAttributesSatisfyingExactly(
                                    messagingAttributes("publish", "sub", clientId))),
                // subscriber: process + publish(response) (another trace because no propagation)
                trace ->
                    trace.hasSpansSatisfyingExactly(
                        span ->
                            span.hasName("sub process").hasKind(SpanKind.CONSUMER).hasNoParent(),
                        span ->
                            span.has(
                                    new Condition<>(
                                        data ->
                                            data.getName().startsWith("_INBOX.")
                                                && data.getName().endsWith(" publish"),
                                        "Name condition"))
                                .hasKind(SpanKind.PRODUCER)
                                .hasParent(trace.getSpan(0))),
                // publisher: process (another trace because no propagation)
                trace ->
                    trace.hasSpansSatisfyingExactly(
                        span ->
                            span.has(
                                    new Condition<>(
                                        data ->
                                            data.getName().startsWith("_INBOX.")
                                                && data.getName().endsWith(" process"),
                                        "Name condition"))
                                .hasKind(SpanKind.CONSUMER)
                                .hasNoParent()
                                .hasAttributesSatisfyingExactly(
                                    messagingAttributes("process", "_INBOX.", clientId)))));

    if (!isInboxMonitored()) {
      asserts.remove(asserts.size() - 1);
    }
    testing().waitAndAssertTraces(asserts);
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
                              span.has(
                                      new Condition<>(
                                          data ->
                                              data.getName().startsWith("_INBOX.")
                                                  && data.getName().endsWith(" publish"),
                                          "Name condition"))
                                  .hasKind(SpanKind.PRODUCER)
                                  .hasParent(trace.getSpan(2)),
                          // publisher: process
                          span ->
                              span.has(
                                      new Condition<>(
                                          data ->
                                              data.getName().startsWith("_INBOX.")
                                                  && data.getName().endsWith(" process"),
                                          "Name condition"))
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(3))
                                  .hasAttributesSatisfyingExactly(
                                      messagingAttributes("process", "_INBOX.", clientId))));

              if (!isInboxMonitored()) {
                asserts.remove(asserts.size() - 1);
              }

              trace.hasSpansSatisfyingExactly(asserts);
            });
  }

  private void assertTimeoutPublishSpan() {
    assertExceptionPublishSpan(new TimeoutException("Timed out waiting for message"));
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
