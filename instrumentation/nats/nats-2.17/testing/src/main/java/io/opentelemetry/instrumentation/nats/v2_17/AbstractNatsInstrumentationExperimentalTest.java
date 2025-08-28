/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.nats.v2_17.NatsInstrumentationTestHelper.messagingAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsInstrumentationExperimentalTest {

  protected abstract InstrumentationExtension testing();

  protected abstract Connection connection();

  private int clientId;

  @BeforeEach
  void beforeEach() {
    clientId = connection().getServerInfo().getClientId();
  }

  @Test
  void testMessagingReceiveAndCapturedHeaders() {
    // given
    Dispatcher dispatcher = connection().createDispatcher(msg -> {}).subscribe("sub");

    // when
    Headers headers = new Headers();
    headers.put("captured-header", "value");
    String traceId =
        testing()
            .runWithSpan(
                "parent",
                () -> {
                  Message message =
                      NatsMessage.builder().subject("sub").headers(headers).data("x").build();
                  connection().publish(message);
                  return Span.fromContext(Context.current()).getSpanContext().getTraceId();
                });
    connection().closeDispatcher(dispatcher);

    // then
    AttributeAssertion[] headerAssert =
        new AttributeAssertion[] {
          equalTo(
              AttributeKey.stringArrayKey("messaging.header.captured_header"),
              singletonList("value"))
        };

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("sub publish")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("publish", "sub", clientId, headerAssert))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("sub receive")
                            .hasKind(SpanKind.CONSUMER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes("receive", "sub", clientId, headerAssert)),
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
