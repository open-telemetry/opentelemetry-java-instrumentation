/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

import java.util.concurrent.ForkJoinTask;

@SuppressWarnings("serial")
final class JavaAsyncQueueWaitChild extends ForkJoinTask<Object> implements TestTask {
  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  private final Long sleepForMillisSeconds;
//  private final boolean doTraceableWork;

  JavaAsyncQueueWaitChild(Long sleepForMillisSeconds) {
//    this.doTraceableWork = doTraceableWork;
    this.sleepForMillisSeconds = sleepForMillisSeconds;
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

//   test task method
  @Override
  public void unblock() {
//    blockThread.set(false);
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

  // test task method
  @Override
  public void waitForCompletion() {
//    try {
//      latch.await();
//    } catch (InterruptedException e) {
//      Thread.currentThread().interrupt();
//      throw new AssertionError(e);
//    }
  }

  private void runImpl() {
    try {
      Thread.sleep(sleepForMillisSeconds);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
    //    while (blockThread.get()) {
      // busy-wait to block thread
//    }
//    if (doTraceableWork) {
//      asyncChild();
//    }
//    latch.countDown();
  }

  private static void asyncChild() {
    tracer.spanBuilder("asyncChild").startSpan().end();
  }
}
