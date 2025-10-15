/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.CALLABLE_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.FORKJOINTASK_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.RUNNABLE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrument {@link ForkJoinTask}.
 *
 * <p>Note: There are quite a few separate implementations of {@code ForkJoinTask}/{@code
 * ForkJoinPool}: JVM, Akka, Scala, Netty to name a few. This class handles JVM version.
 */
public class JavaForkJoinTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(ForkJoinTask.class.getName()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("exec").and(takesArguments(0)).and(not(isAbstract())),
        JavaForkJoinTaskInstrumentation.class.getName() + "$ForkJoinTaskAdvice");
    transformer.applyAdviceToMethod(
        named("fork").and(takesArguments(0)),
        JavaForkJoinTaskInstrumentation.class.getName() + "$ForkAdvice");
  }

  @SuppressWarnings("unused")
  public static class ForkJoinTaskAdvice {

    /**
     * When {@link ForkJoinTask} object is submitted to {@link ForkJoinPool} as {@link Runnable} or
     * {@link Callable} it will not get wrapped, instead it will be casted to {@code ForkJoinTask}
     * directly. This means state is still stored in {@code Runnable} or {@code Callable} and we
     * need to use that state.
     */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope enter(@Advice.This ForkJoinTask<?> task) {
      Scope scope =
          TaskAdviceHelper.makePropagatedContextCurrent(FORKJOINTASK_PROPAGATED_CONTEXT, task);
      if (task instanceof Runnable) {
        Scope newScope =
            TaskAdviceHelper.makePropagatedContextCurrent(
                RUNNABLE_PROPAGATED_CONTEXT, (Runnable) task);
        if (null != newScope) {
          if (null != scope) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      if (task instanceof Callable) {
        Scope newScope =
            TaskAdviceHelper.makePropagatedContextCurrent(
                CALLABLE_PROPAGATED_CONTEXT, (Callable<?>) task);
        if (null != newScope) {
          if (null != scope) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ForkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enterFork(@Advice.This ForkJoinTask<?> task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, FORKJOINTASK_PROPAGATED_CONTEXT, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitFork(
        @Advice.This ForkJoinTask<?> task,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, FORKJOINTASK_PROPAGATED_CONTEXT, task);
    }
  }
}
