/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public final class TaskContextHolder implements ImplicitContextKeyed {

  private static final ContextKey<TaskContextHolder> KEY =
      named("opentelemetry-spring-scheduling-task");

  @Nullable private Context taskContext;

  private TaskContextHolder() {}

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new TaskContextHolder());
  }

  public static void set(Context taskContext) {
    TaskContextHolder holder = taskContext.get(KEY);
    if (holder != null) {
      holder.taskContext = taskContext;
    }
  }

  @Nullable
  public static Context getTaskContext(Context context) {
    Context taskContext = null;
    TaskContextHolder holder = context.get(KEY);
    if (holder != null) {
      taskContext = holder.taskContext;
    }
    return taskContext;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
