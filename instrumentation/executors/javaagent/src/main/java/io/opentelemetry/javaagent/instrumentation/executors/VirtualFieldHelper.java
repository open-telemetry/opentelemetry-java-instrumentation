/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

public final class VirtualFieldHelper {
  public static final VirtualField<Runnable, PropagatedContext> RUNNABLE_PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);

  public static final VirtualField<ForkJoinTask<?>, PropagatedContext>
      FORKJOINTASK_PROPAGATED_CONTEXT =
          VirtualField.find(ForkJoinTask.class, PropagatedContext.class);

  public static final VirtualField<Future<?>, PropagatedContext> FUTURE_PROPAGATED_CONTEXT =
      VirtualField.find(Future.class, PropagatedContext.class);

  public static final VirtualField<Callable<?>, PropagatedContext> CALLABLE_PROPAGATED_CONTEXT =
      VirtualField.find(Callable.class, PropagatedContext.class);

  private VirtualFieldHelper() {}
}
