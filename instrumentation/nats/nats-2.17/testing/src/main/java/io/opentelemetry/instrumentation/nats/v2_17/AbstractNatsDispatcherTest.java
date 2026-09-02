/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.nats.v2_17.NatsTestHelper.messagingAttributes;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  void testProcessMetrics() throws InterruptedException {
    CountDownLatch handled = new CountDownLatch(1);
    Dispatcher dispatcher =
        connection.createDispatcher(msg -> handled.countDown()).subscribe("metrics");
    cleanup.deferCleanup(() -> connection.closeDispatcher(dispatcher));

    connection.publish("metrics", new byte[] {0});

    assertThat(handled.await(10, SECONDS)).isTrue();
    assertProcessMetrics("metrics", null);
  }

  @Test
  void testProcessErrorMetrics() throws InterruptedException {
    CountDownLatch handled = new CountDownLatch(1);
    Dispatcher dispatcher =
        connection
            .createDispatcher(
                msg -> {
                  handled.countDown();
                  throw new IllegalStateException("test");
                })
            .subscribe("error");
    cleanup.deferCleanup(() -> connection.closeDispatcher(dispatcher));

    connection.publish("error", new byte[] {0});

    assertThat(handled.await(10, SECONDS)).isTrue();
    assertProcessMetrics("error", IllegalStateException.class.getName());
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

  @Test
  void testCapturedHeaders() {
    // given
    Dispatcher dispatcher = connection.createDispatcher(msg -> {}).subscribe("sub");
    cleanup.deferCleanup(() -> connection.closeDispatcher(dispatcher));

    // when
    Headers headers = new Headers();
    headers.put("Test-Message-Header", "test");
    headers.put("Uncaptured-Header", "password");
    testing()
        .runWithSpan(
            "parent",
            () -> {
              Message message =
                  NatsMessage.builder().subject("sub").headers(headers).data("x").build();
              connection.publish(message);
            });

    // then
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "publish sub" : "sub publish")
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                messagingAttributes(
                                    "publish",
                                    "sub",
                                    clientId,
                                    equalTo(
                                        MessageHeaderUtil.headerAttributeKey("Test-Message-Header"),
                                        singletonList("test")))),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "process sub" : "sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasLinksSatisfying(expectedProcessLinks(trace.getSpan(1)))));
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
                        span.hasName(emitStableMessagingSemconv() ? "publish sub" : "sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "process sub" : "sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(1))
                            .hasLinksSatisfying(expectedProcessLinks(trace.getSpan(1))),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(2)),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "publish sub" : "sub publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasParent(trace.getSpan(0)),
                    span ->
                        span.hasName(emitStableMessagingSemconv() ? "process sub" : "sub process")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(4))
                            .hasLinksSatisfying(expectedProcessLinks(trace.getSpan(4)))
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

  /**
   * Returns an assertion for the links expected on a process span whose message was published by
   * {@code publishSpan}. The new conventions ask for a link to the message creation context, the
   * old ones do not use links at all.
   */
  private static Consumer<List<? extends LinkData>> expectedProcessLinks(SpanData publishSpan) {
    return links -> {
      if (!emitStableMessagingSemconv()) {
        assertThat(links).isEmpty();
        return;
      }
      assertThat(links)
          .singleElement()
          .satisfies(
              link -> {
                assertThat(link.getSpanContext().getTraceId())
                    .isEqualTo(publishSpan.getSpanContext().getTraceId());
                assertThat(link.getSpanContext().getSpanId())
                    .isEqualTo(publishSpan.getSpanContext().getSpanId());
              });
    };
  }
}
