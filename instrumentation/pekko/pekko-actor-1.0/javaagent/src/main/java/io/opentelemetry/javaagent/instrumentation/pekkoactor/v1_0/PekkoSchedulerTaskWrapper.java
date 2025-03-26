/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import io.opentelemetry.context.Context;
import org.apache.pekko.actor.Scheduler;

public final class PekkoSchedulerTaskWrapper {

  public static Runnable wrap(Runnable runnable) {
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13066
    // Skip wrapping shutdown tasks. Shutdown process hangs when shutdown tasks are wrapped here.
    if (runnable instanceof Scheduler.TaskRunOnClose) {
      return runnable;
    }

    Context context = Context.current();
    return context.wrap(runnable);
  }

  private PekkoSchedulerTaskWrapper() {}
}
