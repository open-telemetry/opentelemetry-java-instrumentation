/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VirtualThreadTest {

  @Test
  void testDisableContextPropagation() throws Exception {
    TestRunnable testRunnable = new TestRunnable();
    Thread thread = Thread.ofVirtual().start(testRunnable);
    thread.join();

    assertThat(testRunnable.error).isNull();
    assertThat(testRunnable.isPropagationDisabled.get()).isTrue();
  }

  private static void executeOnCarrierThread(Callable<?> callable) throws Exception {
    // call VirtualThread.executeOnCarrierThread, VirtualThreadInstrumentation disables context
    // propagation inside that method
    Method executeOnCarrierThreadMethod =
        Class.forName("java.lang.VirtualThread")
            .getDeclaredMethod("executeOnCarrierThread", Callable.class);
    executeOnCarrierThreadMethod.setAccessible(true);
    executeOnCarrierThreadMethod.invoke(Thread.currentThread(), callable);
  }

  static class TestRunnable implements Runnable {
    AtomicBoolean isPropagationDisabled = new AtomicBoolean();
    Exception error;

    @Override
    public void run() {
      try {
        executeOnCarrierThread(
            () -> {
              isPropagationDisabled.set(ExecutorAdviceHelper.isPropagationDisabled());
              return null;
            });
      } catch (Exception exception) {
        error = exception;
      }
    }
  }
}
