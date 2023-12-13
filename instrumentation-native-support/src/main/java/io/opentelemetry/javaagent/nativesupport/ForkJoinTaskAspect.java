/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;

/**
 * Match subclasses of {@link ForkJoinTask} provided by JDK.
 * This is the native image version of {@code io.opentelemetry.javaagent.instrumentation.executors.JavaForkJoinTaskInstrumentation}.
 */
@SuppressWarnings({"OtelPrivateConstructorForUtilityClass", "EmptyCatch"})
@Aspect(subClassOf = "java.util.concurrent.ForkJoinTask", onlyWith = Aspect.JDKClassOnly.class)
public class ForkJoinTaskAspect {
  @Advice.Before
  public static Scope exec(@Advice.This ForkJoinTask<?> thisRef) {
    try {
      VirtualField<ForkJoinTask<?>, PropagatedContext> virtualField = VirtualField
          .find(ForkJoinTask.class,
              PropagatedContext.class);
      Scope scope = TaskAdviceHelper.makePropagatedContextCurrent(virtualField, thisRef);
      if (thisRef instanceof Runnable) {
        VirtualField<Runnable, PropagatedContext> runnableVirtualField = VirtualField
            .find(Runnable.class,
                PropagatedContext.class);
        Scope newScope = TaskAdviceHelper
            .makePropagatedContextCurrent(runnableVirtualField, (Runnable) thisRef);
        if (null != newScope) {
          if (null != scope) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      if (thisRef instanceof Callable) {
        VirtualField<Callable<?>, PropagatedContext> callableVirtualField = VirtualField
            .find(Callable.class,
                PropagatedContext.class);
        Scope newScope = TaskAdviceHelper.makePropagatedContextCurrent(
            callableVirtualField, (Callable<?>) thisRef);
        if (null != newScope) {
          if (null != scope) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      return scope;
    } catch (Throwable t) {
      // suppress exception
      return null;
    }
  }

  @Advice.After(value = "exec", onThrowable = Throwable.class)
  public static void afterExec(@Advice.BeforeResult Scope scope) {
    try {
      if (scope != null) {
        scope.close();
      }
    } catch (Throwable t) {

    }

  }

  @Advice.Before("fork")
  public static PropagatedContext beforeFork(@Advice.This ForkJoinTask<?> task) {
    try {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        VirtualField<ForkJoinTask<?>, PropagatedContext> virtualField = VirtualField
            .find(ForkJoinTask.class,
                PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, task);
      }
      return null;
    } catch (Throwable t) {
      return null;
    }
  }

  @Advice.After(value = "fork", onThrowable = Throwable.class)
  public static void afterFork(
      @Advice.BeforeResult PropagatedContext propagatedContext,
      @Advice.Thrown Throwable throwable) {
    ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable);
  }
}
