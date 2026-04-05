/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.undertow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class UndertowActiveHandlersTest {

  // -----------------------------------------------------------------------
  // Null-safety: CONTEXT_KEY was never set (init() was never called)
  // -----------------------------------------------------------------------

  @Test
  void increment_doesNotThrowOnUninitializedContext() {
    assertThatCode(() -> UndertowActiveHandlers.increment(Context.root()))
        .doesNotThrowAnyException();
  }

  @Test
  void decrementAndGet_doesNotThrowOnUninitializedContext() {
    assertThatCode(() -> UndertowActiveHandlers.decrementAndGet(Context.root()))
        .doesNotThrowAnyException();
  }

  @Test
  void decrementAndGet_returnsSafeNonZeroOnUninitializedContext() {
    // Callers check `== 0` to decide whether to end a span; returning a non-zero value
    // ensures no span is incorrectly ended when the counter was never initialized.
    int result = UndertowActiveHandlers.decrementAndGet(Context.root());
    assertThat(result).isNotEqualTo(0);
  }

  // -----------------------------------------------------------------------
  // Happy path: counter is initialized via init()
  // -----------------------------------------------------------------------

  @Test
  void init_setsCounterToInitialValue() {
    Context ctx = UndertowActiveHandlers.init(Context.root(), 2);
    // After init(2), one decrement should yield 1 (not yet zero → span stays open).
    assertThat(UndertowActiveHandlers.decrementAndGet(ctx)).isEqualTo(1);
  }

  @Test
  void decrementAndGet_reachesZeroAfterAllDecrementsOnInitializedContext() {
    Context ctx = UndertowActiveHandlers.init(Context.root(), 2);
    UndertowActiveHandlers.decrementAndGet(ctx); // → 1
    assertThat(UndertowActiveHandlers.decrementAndGet(ctx)).isEqualTo(0);
  }

  @Test
  void increment_increasesCounterOnInitializedContext() {
    // Start at 2, increment once → 3, then three decrements reach 0.
    Context ctx = UndertowActiveHandlers.init(Context.root(), 2);
    UndertowActiveHandlers.increment(ctx);
    UndertowActiveHandlers.decrementAndGet(ctx); // → 2
    UndertowActiveHandlers.decrementAndGet(ctx); // → 1
    assertThat(UndertowActiveHandlers.decrementAndGet(ctx)).isEqualTo(0);
  }

  // -----------------------------------------------------------------------
  // Mixed: increment on uninitialized context should remain a no-op even
  // when the same context object is later used with a proper counter.
  // -----------------------------------------------------------------------

  @Test
  void increment_isNoOpOnUninitializedContext() {
    // Incrementing a context that has no counter must not affect a fresh
    // initialized context created from the same root.
    Context uninit = Context.root();
    UndertowActiveHandlers.increment(uninit); // no-op

    Context init = UndertowActiveHandlers.init(Context.root(), 2);
    UndertowActiveHandlers.decrementAndGet(init); // → 1
    // If increment had mutated shared state the value here would be wrong.
    assertThat(UndertowActiveHandlers.decrementAndGet(init)).isEqualTo(0);
  }
}
