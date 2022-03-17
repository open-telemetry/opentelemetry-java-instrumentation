/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaAsyncChild extends ForkJoinTask<Object> implements Runnable, Callable<Object> {
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;
  private final CountDownLatch latch = new CountDownLatch(1);

  public JavaAsyncChild() {
    this(/* doTraceableWork= */ true, /* blockThread= */ false);
  }

  public JavaAsyncChild(boolean doTraceableWork, boolean blockThread) {
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
