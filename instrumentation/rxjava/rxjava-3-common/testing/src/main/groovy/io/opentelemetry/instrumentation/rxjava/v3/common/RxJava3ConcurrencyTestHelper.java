/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava.v3.common;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This test creates the specified number of traces with three spans: 1) Outer (root) span 2) Middle
 * span, child of outer, created in success handler of the chain subscribed to in the context of the
 * outer span (with some delay and map thrown in for good measure) 3) Inner span, child of middle,
 * created in the success handler of a new chain started and subscribed to in the the middle span
 *
 * <p>The varying delays between the stages where each span is created should guarantee that
 * scheduler threads handling various stages of the chain will have to alternate between contexts
 * from different traces.
 */
public class RxJava3ConcurrencyTestHelper {
  public static void launchAndWait(
      Scheduler scheduler, int iterations, long timeoutMillis, InstrumentationTestRunner runner) {
    CountDownLatch latch = new CountDownLatch(iterations);

    for (int i = 0; i < iterations; i++) {
      launchOuter(new Iteration(scheduler, latch, i), runner);
    }

    try {
      // Continue even on timeout so the test assertions can show what is missing
      //noinspection ResultOfMethodCallIgnored
      latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void launchOuter(Iteration iteration, InstrumentationTestRunner runner) {
    runner.runWithSpan(
        "outer",
        () -> {
          Span.current().setAttribute("iteration", iteration.index);

          Single.fromCallable(() -> iteration)
              .subscribeOn(iteration.scheduler)
              .observeOn(iteration.scheduler)
              // Use varying delay so that different stages of the chain would alternate.
              .delay(iteration.index % 10, TimeUnit.MILLISECONDS, iteration.scheduler)
              .map((it) -> it)
              .delay(iteration.index % 10, TimeUnit.MILLISECONDS, iteration.scheduler)
              .doOnSuccess(v -> launchInner(v, runner))
              .subscribe();
        });
  }

  private static void launchInner(Iteration iteration, InstrumentationTestRunner runner) {
    runner.runWithSpan(
        "middle",
        () -> {
          Span.current().setAttribute("iteration", iteration.index);

          Single.fromCallable(() -> iteration)
              .subscribeOn(iteration.scheduler)
              .observeOn(iteration.scheduler)
              .delay(iteration.index % 10, TimeUnit.MILLISECONDS, iteration.scheduler)
              .doOnSuccess(
                  (it) -> {
                    runner.runWithSpan(
                        "inner",
                        () -> {
                          Span.current().setAttribute("iteration", it.index);
                          return null;
                        });
                    it.countDown.countDown();
                  })
              .subscribe();

          return null;
        });
  }

  private static class Iteration {
    public final Scheduler scheduler;
    public final CountDownLatch countDown;
    public final int index;

    private Iteration(Scheduler scheduler, CountDownLatch countDown, int index) {
      this.scheduler = scheduler;
      this.countDown = countDown;
      this.index = index;
    }
  }
}
