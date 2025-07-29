/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import akka.dispatch.forkjoin.ForkJoinTask;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import java.util.concurrent.Callable;

public class VirtualFields {

  private VirtualFields() {}

  public static final VirtualField<ForkJoinTask<?>, PropagatedContext>
      FORK_JOIN_TASK_PROPAGATED_CONTEXT =
          VirtualField.find(ForkJoinTask.class, PropagatedContext.class);
  public static final VirtualField<Runnable, PropagatedContext> RUNNABLE_PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);
  public static final VirtualField<Callable<?>, PropagatedContext> CALLABLE_PROPAGATED_CONTEXT =
      VirtualField.find(Callable.class, PropagatedContext.class);
}
