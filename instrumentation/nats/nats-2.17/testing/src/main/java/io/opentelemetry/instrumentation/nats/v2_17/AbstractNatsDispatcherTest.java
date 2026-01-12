/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.messagingAttributes;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsDispatcherTest extends AbstractNatsTest {

  private int clientId;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
  }

  @Test
  void testSubscribeDefaultHandler() {
    Dispatcher d1 = connection.createDispatcher(msg -> addChildSpan()).subscribe("sub");

    publishAndAssertTraceAndSpans();

    // finally, to make sure we're unwrapping properly the
    // OpenTelemetryDispatcher in the library
    assertThatNoException().isThrownBy(() -> connection.closeDispatcher(d1));
  }

  @Test
  void testSubscribeSubscriptionMessageHandler() {
    Dispatcher d1 = connection.createDispatcher();
    Subscription s1 = d1.subscribe("sub", msg -> addChildSpan());

    publishAndAssertTraceAndSpans();

    // finally, to make sure we're unwrapping properly the
    // OpenTelemetryDispatcher in the library
    assertThatNoException()
        .isThrownBy(
            () -> {
              d1.unsubscribe(s1);
              connection.closeDispatcher(d1);
            });
  }

  @Test
  void testSubscribeSubscriptionQueueMessageHandler() {
    Dispatcher d1 = connection.createDispatcher();
    Subscription s1 = d1.subscribe("sub", "queue", msg -> addChildSpan());

    publishAndAssertTraceAndSpans();

    // finally, to make sure we're unwrapping properly the
    // OpenTelemetryDispatcher in the library
    assertThatNoException()
        .isThrownBy(
            () -> {
              d1.unsubscribe(s1);
              connection.closeDispatcher(d1);
            });
  }

  void publishAndAssertTraceAndSpans() {
    // when
    testing()
        .runWithSpan(
            "parent",
            () -> {
              NatsMessage.Builder builder = NatsMessage.builder().subject("sub").data("x");
              connection.publish(builder.build());
              connection.publish(builder.headers(new Headers()).build());
            });

    // then 1 trace
    // - parent
    // --- 1 publish
    // ----- process (propagation with explicit headers)
    // -------- test
    // --- 1 publish
    // ----- process (propagation with headers override)
    // -------- test
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)),
                    span ->
                        span.hasName("sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(4))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("process", "sub", clientId)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(5))));
  }

  void addChildSpan() {
    testing().runWithSpan("child", () -> {});
  }
}
