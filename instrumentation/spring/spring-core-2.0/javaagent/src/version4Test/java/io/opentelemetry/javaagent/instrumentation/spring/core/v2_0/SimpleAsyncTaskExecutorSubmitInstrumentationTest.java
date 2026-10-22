/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.core.v2_0.AsyncTaskTestUtil.executeTwoTasks;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class SimpleAsyncTaskExecutorSubmitInstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();

  private static final Method submitListenableRunnable;
  private static final Method submitListenableCallable;

  static {
    // removed in spring 7
    submitListenableRunnable = findMethod("submitListenable", Runnable.class);
    submitListenableCallable = findMethod("submitListenable", Callable.class);
  }

  private static Method findMethod(String name, Class<?>... parameterTypes) {
    try {
      return SimpleAsyncTaskExecutor.class.getMethod(name, parameterTypes);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Test
  void submitRunnable() {
    executeTwoTasks(testing, task -> executor.submit((Runnable) task));
  }

  @Test
  void submitCallable() {
    executeTwoTasks(testing, task -> executor.submit((Callable<?>) task));
  }

  @Test
  void submitListenableRunnable() {
    assumeTrue(submitListenableRunnable != null);
    executeTwoTasks(testing, task -> submitListenableRunnable.invoke(executor, task));
  }

  @Test
  void submitListenableCallable() {
    assumeTrue(submitListenableCallable != null);
    executeTwoTasks(testing, task -> submitListenableCallable.invoke(executor, task));
  }
}
