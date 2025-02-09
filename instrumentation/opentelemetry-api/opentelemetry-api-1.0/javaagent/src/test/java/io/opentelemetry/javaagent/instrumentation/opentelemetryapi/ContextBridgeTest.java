/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@SuppressWarnings("deprecation") // using deprecated semconv
class ContextBridgeTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ContextKey<String> ANIMAL = ContextKey.named("animal");

  @Test
  @DisplayName("agent propagates application's context")
  void agentPropagatesApplicationsContext() throws Exception {
    // When
    Context context = Context.current().with(ANIMAL, "cat");
    AtomicReference<String> captured = new AtomicReference<>();
    try (Scope ignored = context.makeCurrent()) {
      Executors.newSingleThreadExecutor()
          .submit(() -> captured.set(Context.current().get(ANIMAL)))
          .get();
    }

    // Then
    assertThat(captured.get()).isEqualTo("cat");
  }

  @Test
  @DisplayName("application propagates agent's context")
  void applicationPropagatesAgentsContext() {
    // Given
    Runnable runnable =
        new Runnable() {
          @WithSpan("test")
          @Override
          public void run() {
            // using @WithSpan above to make the agent generate a context
            // and then using manual propagation below to verify that context can be propagated by
            // user
            Context context = Context.current();
            try (Scope ignored = Context.root().makeCurrent()) {
              Span.current().setAttribute("dog", "no");
              try (Scope ignored2 = context.makeCurrent()) {
                Span.current().setAttribute("cat", "yes");
              }
            }
          }
        };

    // When
    runnable.run();
    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                runnable.getClass().getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "run"),
                            equalTo(stringKey("cat"), "yes"))));
  }

  @Test
  @DisplayName("agent propagates application's span")
  void agentPropagatesApplicationsSpan() throws Exception {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");

    Span testSpan = tracer.spanBuilder("test").startSpan();
    try (Scope ignored = testSpan.makeCurrent()) {
      Executors.newSingleThreadExecutor()
          .submit(
              () -> {
                Span.current().setAttribute("cat", "yes");
              })
          .get();
    }
    testSpan.end();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(equalTo(stringKey("cat"), "yes"))));
  }

  @Test
  @DisplayName("application propagates agent's span")
  void applicationPropagatesAgentsSpan() {
    // Given
    Runnable runnable =
        new Runnable() {
          @WithSpan("test")
          @Override
          public void run() {
            // using @WithSpan above to make the agent generate a span
            // and then using manual propagation below to verify that span can be propagated by user
            Span span = Span.current();
            try (Scope ignored = Context.root().makeCurrent()) {
              Span.current().setAttribute("dog", "no");
              try (Scope ignored2 = span.makeCurrent()) {
                Span.current().setAttribute("cat", "yes");
              }
            }
          }
        };

    // When
    runnable.run();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                runnable.getClass().getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "run"),
                            equalTo(stringKey("cat"), "yes"))));
  }

  @Test
  @DisplayName("agent propagates application's baggage")
  void agentPropagatesApplicationsBaggage() throws Exception {
    // When
    Baggage testBaggage = Baggage.builder().put("cat", "yes").build();
    AtomicReference<Baggage> ref = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    try (Scope ignored = testBaggage.makeCurrent()) {
      Executors.newSingleThreadExecutor()
          .submit(
              () -> {
                ref.set(Baggage.current());
                latch.countDown();
              })
          .get();
    }

    // Then
    latch.await();
    assertThat(ref.get().size()).isEqualTo(1);
    assertThat(ref.get().getEntryValue("cat")).isEqualTo("yes");
  }

  @Test
  @DisplayName("test empty current context is root context")
  void testEmptyCurrentContextIsRootContext() {
    // Expect
    assertThat(Context.current()).isEqualTo(Context.root());
  }

  // TODO (trask)
  // more tests are needed here, not sure how to implement, probably need to write some test
  // instrumentation to help test, similar to :testing-common:integration-tests
  //
  // * "application propagates agent's baggage"
  // * "agent uses application's span"
  // * "application uses agent's span" (this is covered above by "application propagates agent's
  // span")
  // * "agent uses application's baggage"
  // * "application uses agent's baggage"
}
