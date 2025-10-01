/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AsyncEventBusTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static final ExecutorService executor = Executors.newSingleThreadExecutor();
  static final AsyncEventBus asyncEventBus = new AsyncEventBus(executor);

  @Test
  void contextPropagation() {
    class Listener {

      @Subscribe
      public void onEvent(String event) {
        testing.runWithSpan("listener", () -> {});
      }
    }

    asyncEventBus.register(new Listener());

    testing.runWithSpan("parent", () -> asyncEventBus.post("test"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"), span -> span.hasName("listener")));
  }
}
