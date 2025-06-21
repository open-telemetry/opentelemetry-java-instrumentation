/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import static io.opentelemetry.instrumentation.nats.v2_21.NatsInstrumentationTestHelper.messagingAttributes;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsInstrumentationMessagingReceiveTest {

  protected abstract InstrumentationExtension testing();

  protected abstract Connection connection();

  private int clientId;

  @BeforeEach
  void beforeEach() {
    clientId = connection().getServerInfo().getClientId();
  }

  @Test
  void testSubscribe() {
    // given
    Dispatcher dispatcher = connection().createDispatcher(msg -> {}).subscribe("sub");

    // when
    String traceId =
        testing()
            .runWithSpan(
                "parent",
                () -> {
                  connection()
                      .publish(
                          NatsMessage.builder()
                              .subject("sub")
                              .headers(new Headers())
                              .data("x")
                              .build());
                  return Span.fromContext(Context.current()).getSpanContext().getTraceId();
                });
    connection().closeDispatcher(dispatcher);

    // then
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span -> span.hasName("sub publish").hasParent(trace.getSpan(0))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("sub receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("receive", "sub", clientId)),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link ->
                                                assertThat(link.getSpanContext().getTraceId())
                                                    .isEqualTo(traceId)))));
  }
}
