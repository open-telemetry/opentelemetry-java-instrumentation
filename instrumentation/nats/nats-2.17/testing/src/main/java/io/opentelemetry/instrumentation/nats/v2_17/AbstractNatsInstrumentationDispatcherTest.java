/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.messagingAttributes;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsInstrumentationDispatcherTest
    extends AbstractNatsInstrumentationTest {

  private int clientId;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
  }

  @Test
  void testSubscribe() {
    // global message handler
    Dispatcher d1 = connection.createDispatcher(msg -> {}).subscribe("sub");
    // per-subscription message handler
    Dispatcher d2 = connection.createDispatcher();
    Subscription s1 = d2.subscribe("sub", msg -> {});
    Subscription s2 = d2.subscribe("sub", "queue", msg -> {});

    // when
    testing()
        .runWithSpan(
            "parent",
            () -> {
              NatsMessage.Builder builder = NatsMessage.builder().subject("sub").data("x");
              connection.publish(builder.build()); // no propagation
              connection.publish(builder.headers(new Headers()).build()); // propagation
            });

    // then 1 trace
    // - parent
    // --- 1 publish
    // ----- process global (propagation with explicit headers)
    // ----- process subject (propagation with explicit headers)
    // ----- process subject+queue (propagation with explicit headers)
    // --- 1 publish
    // ----- process global (propagation with headers override)
    // ----- process subject (propagation with headers override)
    // ----- process subject+queue (propagation with headers override)
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
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1)),
                    span ->
                        span.hasName("sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(5)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(5)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(5))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("process", "sub", clientId))));

    // finally, to make sure we're unwrapping properly the
    // OpenTelemetryDispatcher in the library
    assertThatNoException()
        .isThrownBy(
            () -> {
              d2.unsubscribe(s1);
              d2.unsubscribe(s2);
              connection.closeDispatcher(d1);
              connection.closeDispatcher(d2);
            });
  }
}
