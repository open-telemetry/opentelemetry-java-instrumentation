/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import io.opentelemetry.context.Context;

public final class AkkaSchedulerTaskWrapper {
  private static final Class<?> RUN_ON_CLOSE_TASK_CLASS = getRunOnCloseTaskClass();

  private static Class<?> getRunOnCloseTaskClass() {
    try {
      return Class.forName("akka.actor.Scheduler$TaskRunOnClose");
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static boolean isRunOnCloseTask(Runnable runnable) {
    return RUN_ON_CLOSE_TASK_CLASS != null && RUN_ON_CLOSE_TASK_CLASS.isInstance(runnable);
  }

  public static Runnable wrap(Runnable runnable) {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13066
    // Skip wrapping shutdown tasks. Shutdown process hangs when shutdown tasks are wrapped here.
    if (isRunOnCloseTask(runnable)) {
      return runnable;
    }

    Context context = Context.current();
    return context.wrap(runnable);
  }

  private AkkaSchedulerTaskWrapper() {}
}
