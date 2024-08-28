/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(AgentInstrumentationExtension.class)
public class ContextBridgeTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ContextKey<String> ANIMAL = ContextKey.named("animal");

  @Test
  @DisplayName("agent propagates application's context")
  public void agentPropagatesApplicationsContext() throws Exception {
    // When
    Context context = Context.current().with(ANIMAL, "cat");
    AtomicReference<String> captured = new AtomicReference<>();
    try (Scope ignored = context.makeCurrent()) {
      Executors.newSingleThreadExecutor()
          .submit(() -> captured.set(Context.current().get(ANIMAL)))
          .get();
    }

    // Then
    assertEquals("cat", captured.get());
  }

  @Test
  @DisplayName("application propagates agent's context")
  public void applicationPropagatesAgentsContext() {
    // Given
    Runnable runnable =
        new Runnable() {
          @WithSpan("test")
          @Override
          public void run() {
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
  public void agentPropagatesApplicationsSpan() throws Exception {
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
  public void applicationPropagatesAgentsSpan() {
    // Given
    Runnable runnable =
        new Runnable() {
          @WithSpan("test")
          @Override
          public void run() {
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
  public void agentPropagatesApplicationsBaggage() throws Exception {
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
    assertEquals(1, ref.get().size());
    assertEquals("yes", ref.get().getEntryValue("cat"));
  }

  @Test
  @DisplayName("test empty current context is root context")
  public void testEmptyCurrentContextIsRootContext() {
    // Expect
    assertEquals(Context.root(), Context.current());
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
