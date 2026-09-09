/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.VirtualFields.RUNNABLE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A pekko dispatcher batches the tasks that scala marks as {@code Batchable}, which since scala
 * 2.13 includes the task that runs the body of a {@code Future}. Rather than being submitted to the
 * underlying pool, such a task is appended to a batch that is already running on the current thread
 * and is run when that batch is drained. The context that the executor instrumentation captures
 * when the batch itself is submitted is the only context those tasks can inherit, so a task that is
 * added to a batch that started before the current context became current runs without it.
 *
 * <p>Attaching the current context to the task when it is handed to the dispatcher gives it a
 * context of its own, which the runnable instrumentation makes current while it runs. The task is
 * not wrapped, so it stays batchable and is batched as before.
 */
class PekkoBatchingExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // scala 2 gives the implementing class a forwarder for the method declared on the trait, scala
    // 3 leaves it on the interface, so both need to be matched
    return hasSuperType(named("org.apache.pekko.dispatch.BatchingExecutor"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArguments(1)).and(takesArgument(0, Runnable.class)),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static PropagatedContext enterExecute(@Advice.Argument(0) Runnable task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        return ExecutorAdviceHelper.attachContextToTask(context, RUNNABLE_PROPAGATED_CONTEXT, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exitExecute(
        @Advice.Argument(0) Runnable task,
        @Advice.Enter @Nullable PropagatedContext propagatedContext,
        @Advice.Thrown @Nullable Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, RUNNABLE_PROPAGATED_CONTEXT, task);
    }
  }
}
