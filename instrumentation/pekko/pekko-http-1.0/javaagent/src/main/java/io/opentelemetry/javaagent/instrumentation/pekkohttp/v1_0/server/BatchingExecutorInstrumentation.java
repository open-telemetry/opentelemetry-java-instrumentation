/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
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
 * 2.13 includes the one that runs the body of a {@code Future}. A batched task is appended to a
 * batch that is already running on the current thread and is run when that batch is drained,
 * without being submitted to the underlying pool. The context that the executor instrumentation
 * captures when the batch itself is submitted is therefore the only context such a task can get,
 * and a task that is added to a batch that started before the current context became current runs
 * without it.
 *
 * <p>Attaching the current context to the task when it is handed to the dispatcher gives it a
 * context of its own, which the runnable instrumentation makes current while it runs.
 */
class BatchingExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.pekko.dispatch.BatchingExecutor");
  }

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
    public static PropagatedContext onEnter(@Advice.Argument(0) Runnable task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, PekkoBatchingHelper.runnableContext(), task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) Runnable task,
        @Advice.Enter @Nullable PropagatedContext propagatedContext,
        @Advice.Thrown @Nullable Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, PekkoBatchingHelper.runnableContext(), task);
    }
  }

  /** Holds the virtual field that the executor instrumentation reads the task context from. */
  public static class PekkoBatchingHelper {
    private static final VirtualField<Runnable, PropagatedContext> RUNNABLE_PROPAGATED_CONTEXT =
        VirtualField.find(Runnable.class, PropagatedContext.class);

    public static VirtualField<Runnable, PropagatedContext> runnableContext() {
      return RUNNABLE_PROPAGATED_CONTEXT;
    }

    private PekkoBatchingHelper() {}
  }
}
