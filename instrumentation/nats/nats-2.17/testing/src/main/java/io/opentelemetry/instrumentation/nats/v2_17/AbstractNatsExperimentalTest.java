/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.messagingAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;

import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractNatsExperimentalTest extends AbstractNatsTest {

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private int clientId;

  @BeforeEach
  void beforeEach() {
    clientId = connection.getServerInfo().getClientId();
  }

  @Test
  void testCapturedHeaders() {
    // given
    Dispatcher dispatcher = connection.createDispatcher(msg -> {}).subscribe("sub");

    // when
    Headers headers = new Headers();
    headers.put("captured-header", "value");
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Message message =
                  NatsMessage.builder().subject("sub").headers(headers).data("x").build();
              connection.publish(message);
            });
    cleanup.deferCleanup(() -> connection.closeDispatcher(dispatcher));

    // then
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("sub publish")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes(
                                    "publish",
                                    "sub",
                                    clientId,
                                    equalTo(
                                        stringArrayKey("messaging.header.captured_header"),
                                        singletonList("value")))),
                    span ->
                        span.hasName("sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))));
  }
}
