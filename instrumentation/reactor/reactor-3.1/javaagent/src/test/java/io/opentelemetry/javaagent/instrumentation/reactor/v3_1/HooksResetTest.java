/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Hooks;

class HooksResetTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ContextKey<String> TEST_CONTEXT_KEY = ContextKey.named("test-context-key");

  @Test
  void contextIsPropagatedAfterResettingAllOnEachOperatorHooks() {
    assertThat(propagatesContextToExternallyDrivenPublisher()).isTrue();

    // the agent re-registers its hook when the application removes all onEachOperator hooks
    Hooks.resetOnEachOperator();

    assertThat(propagatesContextToExternallyDrivenPublisher()).isTrue();

    // and it keeps doing so
    Hooks.resetOnEachOperator();

    assertThat(propagatesContextToExternallyDrivenPublisher()).isTrue();
  }

  @Test
  void contextIsPropagatedAfterResettingUnrelatedOnEachOperatorHook() {
    Hooks.resetOnEachOperator("unrelated-hook");

    assertThat(propagatesContextToExternallyDrivenPublisher()).isTrue();
  }

  // The publisher is driven from outside of the subscription, so the context that is current in the
  // subscriber callback can only come from the subscription time and not from the producer.
  private static boolean propagatesContextToExternallyDrivenPublisher() {
    AtomicReference<FluxSink<String>> sink = new AtomicReference<>();
    AtomicReference<String> observedContextValue = new AtomicReference<>();
    Flux<String> publisher = Flux.create(sink::set);

    try (Scope ignored =
        Context.root().with(TEST_CONTEXT_KEY, "test-context-value").makeCurrent()) {
      publisher.subscribe(
          unused -> observedContextValue.set(Context.current().get(TEST_CONTEXT_KEY)));
    }

    testing.runWithSpan("producer", () -> sink.get().next("message"));
    sink.get().complete();

    return "test-context-value".equals(observedContextValue.get());
  }
}
