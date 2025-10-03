/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.executors.ExecutorMatchers.executorNameMatcher;
import static io.opentelemetry.javaagent.instrumentation.executors.ExecutorMatchers.isExecutor;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.CALLABLE_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.FORKJOINTASK_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.FUTURE_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.executors.VirtualFieldHelper.RUNNABLE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingCallable;
import io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JavaExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return executorNameMatcher().and(isExecutor()); // Apply expensive matcher last.
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(0, Runnable.class)).and(takesArguments(1)),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
    // Netty uses addTask as the actual core of their submission; there are non-standard variations
    // like execute(Runnable,boolean) that aren't caught by standard instrumentation
    transformer.applyAdviceToMethod(
        named("addTask").and(takesArgument(0, Runnable.class)).and(takesArguments(1)),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformer.applyAdviceToMethod(
        named("submit")
            .and(takesArgument(0, Runnable.class))
            .and(returns(hasSuperType(is(Future.class)))),
        JavaExecutorInstrumentation.class.getName() + "$SetSubmitRunnableStateAdvice");
    transformer.applyAdviceToMethod(
        named("submit")
            .and(takesArgument(0, Callable.class))
            .and(returns(hasSuperType(is(Future.class)))),
        JavaExecutorInstrumentation.class.getName() + "$SetCallableStateAdvice");
    transformer.applyAdviceToMethod(
        named("submit").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("invokeAny", "invokeAll").and(takesArgument(0, Collection.class)),
        JavaExecutorInstrumentation.class.getName()
            + "$SetCallableStateForCallableCollectionAdvice");
    transformer.applyAdviceToMethod(
        named("invoke").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformer.applyAdviceToMethod(
        named("schedule")
            .and(takesArgument(0, Runnable.class))
            .and(returns(hasSuperType(is(Future.class)))),
        JavaExecutorInstrumentation.class.getName() + "$SetSubmitRunnableStateAdvice");
    transformer.applyAdviceToMethod(
        named("schedule")
            .and(takesArgument(0, Callable.class))
            .and(returns(hasSuperType(is(Future.class)))),
        JavaExecutorInstrumentation.class.getName() + "$SetCallableStateAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetExecuteRunnableStateAdvice {

    public static class ExecuteRunnableAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final PropagatedContext propagatedContext;
      private final Runnable task;

      private ExecuteRunnableAdviceScope(
          CallDepth callDepth, @Nullable PropagatedContext propagatedContext, Runnable task) {
        this.callDepth = callDepth;
        this.propagatedContext = propagatedContext;
        this.task = task;
      }

      public Runnable getTask() {
        return task;
      }

      public static ExecuteRunnableAdviceScope start(CallDepth callDepth, Runnable task) {
        if (callDepth.getAndIncrement() > 0) {
          return new ExecuteRunnableAdviceScope(callDepth, null, task);
        }
        Context context = Context.current();
        if (!ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
          return new ExecuteRunnableAdviceScope(callDepth, null, task);
        }
        if (ContextPropagatingRunnable.shouldDecorateRunnable(task)) {
          task = ContextPropagatingRunnable.propagateContext(task, context);
          return new ExecuteRunnableAdviceScope(callDepth, null, task);
        }
        PropagatedContext propagatedContext =
            ExecutorAdviceHelper.attachContextToTask(context, RUNNABLE_PROPAGATED_CONTEXT, task);
        return new ExecuteRunnableAdviceScope(callDepth, propagatedContext, task);
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        ExecutorAdviceHelper.cleanUpAfterSubmit(
            propagatedContext, throwable, RUNNABLE_PROPAGATED_CONTEXT, task);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enterJobSubmit(
        @Advice.This Object executor, @Advice.Argument(0) Runnable task) {
      CallDepth callDepth = CallDepth.forClass(executor.getClass());
      ExecuteRunnableAdviceScope adviceScope = ExecuteRunnableAdviceScope.start(callDepth, task);
      return new Object[] {adviceScope, adviceScope.getTask()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Argument(0) Runnable task,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter Object[] enterResult) {
      ExecuteRunnableAdviceScope adviceScope = (ExecuteRunnableAdviceScope) enterResult[0];
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SetJavaForkJoinStateAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enterJobSubmit(@Advice.Argument(0) ForkJoinTask<?> task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, FORKJOINTASK_PROPAGATED_CONTEXT, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Argument(0) ForkJoinTask<?> task,
        @Advice.Enter @Nullable PropagatedContext propagatedContext,
        @Advice.Thrown @Nullable Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, FORKJOINTASK_PROPAGATED_CONTEXT, task);
    }
  }

  @SuppressWarnings("unused")
  public static class SetSubmitRunnableStateAdvice {

    public static class SubmitRunnableAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final PropagatedContext propagatedContext;
      private final Runnable task;

      private SubmitRunnableAdviceScope(
          CallDepth callDepth, @Nullable PropagatedContext propagatedContext, Runnable task) {
        this.callDepth = callDepth;
        this.propagatedContext = propagatedContext;
        this.task = task;
      }

      public Runnable getTask() {
        return task;
      }

      public static SubmitRunnableAdviceScope start(CallDepth callDepth, Runnable task) {
        if (callDepth.getAndIncrement() > 0) {
          return new SubmitRunnableAdviceScope(callDepth, null, task);
        }
        Context context = Context.current();
        if (!ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
          return new SubmitRunnableAdviceScope(callDepth, null, task);
        }
        if (ContextPropagatingRunnable.shouldDecorateRunnable(task)) {
          task = ContextPropagatingRunnable.propagateContext(task, context);
          return new SubmitRunnableAdviceScope(callDepth, null, task);
        }
        PropagatedContext propagatedContext =
            ExecutorAdviceHelper.attachContextToTask(context, RUNNABLE_PROPAGATED_CONTEXT, task);
        return new SubmitRunnableAdviceScope(callDepth, propagatedContext, task);
      }

      public void end(@Nullable Throwable throwable, @Nullable Future<?> future) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (propagatedContext != null && future != null) {
          FUTURE_PROPAGATED_CONTEXT.set(future, propagatedContext);
        }
        ExecutorAdviceHelper.cleanUpAfterSubmit(
            propagatedContext, throwable, RUNNABLE_PROPAGATED_CONTEXT, task);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enterJobSubmit(
        @Advice.This Object executor, @Advice.Argument(0) Runnable task) {
      CallDepth callDepth = CallDepth.forClass(executor.getClass());
      SubmitRunnableAdviceScope adviceScope = SubmitRunnableAdviceScope.start(callDepth, task);
      return new Object[] {adviceScope, adviceScope.getTask()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Argument(0) Runnable task,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Future<?> future,
        @Advice.Enter Object[] enterResult) {
      SubmitRunnableAdviceScope adviceScope = (SubmitRunnableAdviceScope) enterResult[0];
      adviceScope.end(throwable, future);
    }
  }

  @SuppressWarnings("unused")
  public static class SetCallableStateAdvice {

    public static class CallableAdviceScope {
      private final CallDepth callDepth;
      @Nullable private final PropagatedContext propagatedContext;
      private final Callable<?> task;

      private CallableAdviceScope(
          CallDepth callDepth, @Nullable PropagatedContext propagatedContext, Callable<?> task) {
        this.callDepth = callDepth;
        this.propagatedContext = propagatedContext;
        this.task = task;
      }

      public static CallableAdviceScope start(CallDepth callDepth, Callable<?> task) {
        if (callDepth.getAndIncrement() > 0) {
          return new CallableAdviceScope(callDepth, null, task);
        }
        Context context = Context.current();
        if (!ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
          return new CallableAdviceScope(callDepth, null, task);
        }
        if (ContextPropagatingCallable.shouldDecorateCallable(task)) {
          task = ContextPropagatingCallable.propagateContext(task, context);
          return new CallableAdviceScope(callDepth, null, task);
        }

        PropagatedContext propagatedContext =
            ExecutorAdviceHelper.attachContextToTask(context, CALLABLE_PROPAGATED_CONTEXT, task);
        return new CallableAdviceScope(callDepth, propagatedContext, task);
      }

      public Callable<?> getTask() {
        return task;
      }

      public void end(@Nullable Throwable throwable, @Nullable Future<?> future) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }
        if (propagatedContext != null && future != null) {
          FUTURE_PROPAGATED_CONTEXT.set(future, propagatedContext);
        }
        ExecutorAdviceHelper.cleanUpAfterSubmit(
            propagatedContext, throwable, CALLABLE_PROPAGATED_CONTEXT, task);
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] enterJobSubmit(
        @Advice.This Object executor, @Advice.Argument(0) Callable<?> task) {
      CallDepth callDepth = CallDepth.forClass(executor.getClass());
      CallableAdviceScope adviceScope = CallableAdviceScope.start(callDepth, task);
      return new Object[] {adviceScope, adviceScope.getTask()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Argument(0) Callable<?> task,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable Future<?> future,
        @Advice.Enter Object[] enterResult) {
      CallableAdviceScope adviceScope = (CallableAdviceScope) enterResult[0];
      adviceScope.end(throwable, future);
    }
  }

  @SuppressWarnings("unused")
  public static class SetCallableStateForCallableCollectionAdvice {

    public static class CallableCollectionAdviceScope {
      private final Collection<? extends Callable<?>> tasks;
      private final CallDepth callDepth;

      private CallableCollectionAdviceScope(
          CallDepth callDepth, Collection<? extends Callable<?>> tasks) {
        this.callDepth = callDepth;
        this.tasks = tasks;
      }

      public Collection<? extends Callable<?>> getTasks() {
        return tasks;
      }

      public static CallableCollectionAdviceScope start(
          CallDepth callDepth, Collection<? extends Callable<?>> tasks) {
        if (callDepth.getAndIncrement() > 0) {
          return new CallableCollectionAdviceScope(
              callDepth, tasks != null ? tasks : Collections.emptyList());
        }
        Context context = Context.current();

        // first, go through the list and wrap all Callables that need to be wrapped
        List<Callable<?>> list = null;
        for (Callable<?> task : tasks) {
          if (!ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
            continue;
          }
          if (ContextPropagatingCallable.shouldDecorateCallable(task)) {
            // lazily create the list only if we need to
            if (list == null) {
              list = new ArrayList<>();
            }
            list.add(ContextPropagatingCallable.propagateContext(task, context));
          }
        }

        for (Callable<?> task : tasks) {
          if (ExecutorAdviceHelper.shouldPropagateContext(context, task)
              && !ContextPropagatingCallable.shouldDecorateCallable(task)) {
            ExecutorAdviceHelper.attachContextToTask(context, CALLABLE_PROPAGATED_CONTEXT, task);
            // if there are wrapped Callables, we need to add the unwrapped ones as well
            if (list != null) {
              list.add(task);
            }
          }
        }
        // returning tasks and not propagatedContexts to avoid allocating another list just for an
        // edge case (exception)

        return new CallableCollectionAdviceScope(callDepth, list);
      }

      public void end(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0) {
          return;
        }

        /*
         Note1: invokeAny doesn't return any futures so all we need to do for it
         is to make sure we close all scopes in case of an exception.
         Note2: invokeAll does return futures - but according to its documentation
         it actually only returns after all futures have been completed - i.e. it blocks.
         This means we do not need to setup any hooks on these futures, we just need to clear
         any parent spans in case of an error.
         (according to ExecutorService docs and AbstractExecutorService code)
        */
        if (throwable != null && tasks != null) {
          for (Callable<?> task : tasks) {
            if (task != null) {
              PropagatedContext propagatedContext = CALLABLE_PROPAGATED_CONTEXT.get(task);
              ExecutorAdviceHelper.cleanUpAfterSubmit(
                  propagatedContext, throwable, CALLABLE_PROPAGATED_CONTEXT, task);
            }
          }
        }
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] submitEnter(
        @Advice.This Object executor, @Advice.Argument(0) Collection<? extends Callable<?>> tasks) {
      if (tasks == null) {
        return new Object[] {null, null};
      }
      CallDepth callDepth = CallDepth.forClass(executor.getClass());
      CallableCollectionAdviceScope adviceScope =
          CallableCollectionAdviceScope.start(callDepth, tasks);
      return new Object[] {adviceScope, adviceScope.getTasks()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void submitExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      CallableCollectionAdviceScope adviceScope = (CallableCollectionAdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
