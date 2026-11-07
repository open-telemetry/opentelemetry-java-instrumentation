/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * An application that registers a {@code VertxTracer} has opted into Vert.x's own event bus tracing
 * (vertx-opentelemetry). Tracing the same hops again would break the parent links, so the agent
 * must leave that Vertx instance alone.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractVertxNativeTracingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private Vertx vertx;

  protected abstract Vertx createVertxWithTracer() throws Exception;

  protected abstract void closeVertx(Vertx vertx) throws Exception;

  @BeforeAll
  void setUp() throws Exception {
    vertx = createVertxWithTracer();
  }

  @AfterAll
  void tearDown() throws Exception {
    if (vertx != null) {
      closeVertx(vertx);
    }
  }

  @Test
  void standsDown() throws InterruptedException {
    String address = "address.native." + System.nanoTime();
    EventBus eventBus = vertx.eventBus();

    CountDownLatch received = new CountDownLatch(1);
    AtomicBoolean traceHeaderInjected = new AtomicBoolean();
    eventBus.consumer(
        address,
        message -> {
          traceHeaderInjected.set(message.headers().contains("traceparent"));
          received.countDown();
        });

    testing.runWithSpan("parent", () -> eventBus.send(address, "hello"));

    assertThat(received.await(10, SECONDS)).isTrue();
    assertThat(traceHeaderInjected).isFalse();

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()));
  }
}
