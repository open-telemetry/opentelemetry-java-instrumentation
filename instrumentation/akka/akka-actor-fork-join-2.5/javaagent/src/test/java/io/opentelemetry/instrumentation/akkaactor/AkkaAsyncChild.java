/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.akkaactor;

import akka.dispatch.forkjoin.ForkJoinTask;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.javaconcurrent.TestTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

final class AkkaAsyncChild extends ForkJoinTask<Object> implements TestTask {
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;
  private final CountDownLatch latch = new CountDownLatch(1);

  public AkkaAsyncChild(boolean doTraceableWork, boolean blockThread) {
    this.doTraceableWork = doTraceableWork;
    this.blockThread = new AtomicBoolean(blockThread);
  }

  @Override
  public Object getRawResult() {
    return null;
  }

  @Override
  protected void setRawResult(Object value) {}

  @Override
  protected boolean exec() {
    runImpl();
    return true;
  }

  @Override
  public void unblock() {
    blockThread.set(false);
  }

  @Override
  public void run() {
    runImpl();
  }

  @Override
  public Object call() {
    runImpl();
    return null;
  }

  @Override
  public void waitForCompletion() {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private void runImpl() {
    while (blockThread.get()) {
      // busy-wait to block thread
    }
    if (doTraceableWork) {
      asyncChild();
    }
    latch.countDown();
  }

  private static void asyncChild() {
    tracer.spanBuilder("asyncChild").startSpan().end();
  }
}
