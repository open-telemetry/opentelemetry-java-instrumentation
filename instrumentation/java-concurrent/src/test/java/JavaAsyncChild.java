/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavaAsyncChild extends ForkJoinTask implements Runnable, Callable {
  private static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto");

  private final AtomicBoolean blockThread;
  private final boolean doTraceableWork;
  private final CountDownLatch latch = new CountDownLatch(1);

  public JavaAsyncChild() {
    this(true, false);
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

  public JavaAsyncChild(boolean doTraceableWork, boolean blockThread) {
    this.doTraceableWork = doTraceableWork;
    this.blockThread = new AtomicBoolean(blockThread);
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

  public void waitForCompletion() throws InterruptedException {
    latch.await();
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

  private void asyncChild() {
    TRACER.spanBuilder("asyncChild").startSpan().end();
  }
}
