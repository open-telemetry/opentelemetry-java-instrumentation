/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.assertTraceparentHeader;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.messagingAttributes;

import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsInstrumentationPublishTest
    extends AbstractNatsInstrumentationTest {

  private int clientId;
  private Subscription subscription;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
    subscription = connection.subscribe("sub");
  }

  @AfterEach
  void afterEach() throws InterruptedException {
    subscription.drain(Duration.ofSeconds(1));
  }

  @Test
  void testPublishBody() throws InterruptedException {
    // when
    testing().runWithSpan("parent", () -> connection.publish("sub", new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testPublishHeadersBody() throws InterruptedException {
    // when
    testing().runWithSpan("parent", () -> connection.publish("sub", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testPublishReplyToBody() throws InterruptedException {
    // when
    testing().runWithSpan("parent", () -> connection.publish("sub", "rt", new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testPublishReplyToHeadersBody() throws InterruptedException {
    // when
    testing()
        .runWithSpan(
            "parent", () -> connection.publish("sub", "rt", new Headers(), new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testPublishMessage() throws InterruptedException {
    NatsMessage message = NatsMessage.builder().subject("sub").data("x").build();

    // when
    testing().runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  @Test
  void testPublishMessageWithHeaders() throws InterruptedException {
    NatsMessage message =
        NatsMessage.builder().subject("sub").data("x").headers(new Headers()).build();

    // when
    testing().runWithSpan("parent", () -> connection.publish(message));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
  }

  private void assertPublishSpan() {
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
  }
}
