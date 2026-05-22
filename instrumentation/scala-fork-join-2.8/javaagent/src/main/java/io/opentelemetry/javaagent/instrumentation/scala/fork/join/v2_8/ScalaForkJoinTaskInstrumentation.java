/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scala.fork.join.v2_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.scala.fork.join.v2_8.VirtualFields.CALLABLE_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.scala.fork.join.v2_8.VirtualFields.FORK_JOIN_TASK_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.scala.fork.join.v2_8.VirtualFields.RUNNABLE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.forkjoin.ForkJoinPool;
import scala.concurrent.forkjoin.ForkJoinTask;

/**
 * Instrument {@link ForkJoinTask}.
 *
 * <p>Note: There are quite a few separate implementations of {@code ForkJoinTask}/{@code
 * ForkJoinPool}: JVM, Akka, Scala, Netty to name a few. This class handles Scala version.
 */
class ScalaForkJoinTaskInstrumentation implements TypeInstrumentation {

  static final String TASK_CLASS_NAME = "scala.concurrent.forkjoin.ForkJoinTask";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(TASK_CLASS_NAME);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named(TASK_CLASS_NAME));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("exec").and(takesArguments(0)).and(not(isAbstract())),
        getClass().getName() + "$ForkJoinTaskAdvice");
  }

  @SuppressWarnings("unused")
  public static class ForkJoinTaskAdvice {

    /**
     * When {@link ForkJoinTask} object is submitted to {@link ForkJoinPool} as {@link Runnable} or
     * {@link Callable} it will not get wrapped, instead it will be cast to {@code ForkJoinTask}
     * directly. This means state is still stored in {@code Runnable} or {@code Callable} and we
     * need to use that state.
     */
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope enter(@Advice.This ForkJoinTask<?> thiz) {
      Scope scope =
          TaskAdviceHelper.makePropagatedContextCurrent(FORK_JOIN_TASK_PROPAGATED_CONTEXT, thiz);
      if (thiz instanceof Runnable) {
        Scope newScope =
            TaskAdviceHelper.makePropagatedContextCurrent(
                RUNNABLE_PROPAGATED_CONTEXT, (Runnable) thiz);
        if (newScope != null) {
          if (scope != null) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      if (thiz instanceof Callable) {
        Scope newScope =
            TaskAdviceHelper.makePropagatedContextCurrent(
                CALLABLE_PROPAGATED_CONTEXT, (Callable<?>) thiz);
        if (newScope != null) {
          if (scope != null) {
            newScope.close();
          } else {
            scope = newScope;
          }
        }
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
