/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.assertTraceparentHeader;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.messagingAttributes;
import static java.nio.charset.StandardCharsets.US_ASCII;

import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractNatsPublishTest extends AbstractNatsTest {

  private int clientId;
  private Subscription subscription;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
    subscription = connection.subscribe("sub");
    cleanup.deferCleanup(() -> subscription.drain(Duration.ofSeconds(10)));
  }

  @Test
  void testPublishBody() throws InterruptedException {
    // when
    testing().runWithSpan("parent", () -> connection.publish("sub", new byte[] {0}));

    // then
    assertPublishSpan();
    assertTraceparentHeader(subscription);
    assertProducerMetrics("publish", "sub", null);
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

  @Test
  void testSettleJetStreamAckSubjects() {
    String firstAckSubject =
        "$JS.ACK.ingestion-stream.partition-a.1.18822351.18675175.1785834929935121483.14757";
    String secondAckSubject =
        "$JS.ACK.ingestion-stream.partition-a.2.18822352.18675176.1785834929935121484.14756";
    String thirdAckSubject =
        "$JS.ACK.ingestion-stream.partition-a.3.18822353.18675177.1785834929935121485.14755";
    String fourthAckSubject =
        "$JS.ACK.ingestion-stream.partition-a.4.18822354.18675178.1785834929935121486.14754";

    // Settlement operations use generated subjects with per-message values in them.
    testing()
        .runWithSpan(
            "parent",
            () -> {
              connection.publish(firstAckSubject, body("+ACK"));
              connection.publish(secondAckSubject, body("-NAK"));
              connection.publish(thirdAckSubject, body("+WPI"));
              connection.publish(fourthAckSubject, body("+TERM"));
            });

    int clientId = connection.getServerInfo().getClientId();
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    settlementSpan(trace, firstAckSubject, "+ACK", "ack", clientId),
                    settlementSpan(trace, secondAckSubject, "-NAK", "nak", clientId),
                    settlementSpan(trace, thirdAckSubject, "+WPI", "inProgress", clientId),
                    settlementSpan(trace, fourthAckSubject, "+TERM", "term", clientId)));
  }

  private static byte[] body(String body) {
    return body.getBytes(US_ASCII);
  }

  private static Consumer<SpanDataAssert> settlementSpan(
      TraceAssert trace, String subject, String body, String operation, int clientId) {
    boolean stable = emitStableMessagingSemconv();
    return span ->
        span.hasName(stable ? operation + " $JS.ACK" : "$JS.ACK settle")
            .hasKind(SpanKind.CLIENT)
            .hasParent(trace.getSpan(0))
            .hasAttributesSatisfyingExactly(
                messagingAttributes(operation, subject, clientId, body.length()));
  }

  private void assertPublishSpan() {
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "publish sub" : "sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("publish", "sub", clientId))));
  }
}
