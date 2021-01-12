/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.javaconcurrent;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.CallableWrapper;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JavaExecutorInstrumentation extends AbstractExecutorInstrumentation {

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute").and(takesArgument(0, Runnable.class)).and(takesArguments(1)),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
    // Netty uses addTask as the actual core of their submission; there are non-standard variations
    // like execute(Runnable,boolean) that aren't caught by standard instrumentation
    transformers.put(
        named("addTask").and(takesArgument(0, Runnable.class)).and(takesArguments(1)),
        JavaExecutorInstrumentation.class.getName() + "$SetExecuteRunnableStateAdvice");
    transformers.put(
        named("execute").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformers.put(
        named("submit").and(takesArgument(0, Runnable.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetSubmitRunnableStateAdvice");
    transformers.put(
        named("submit").and(takesArgument(0, Callable.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetCallableStateAdvice");
    transformers.put(
        named("submit").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformers.put(
        nameMatches("invoke(Any|All)$").and(takesArgument(0, Collection.class)),
        JavaExecutorInstrumentation.class.getName()
            + "$SetCallableStateForCallableCollectionAdvice");
    transformers.put(
        nameMatches("invoke").and(takesArgument(0, ForkJoinTask.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetJavaForkJoinStateAdvice");
    transformers.put(
        named("schedule").and(takesArgument(0, Runnable.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetSubmitRunnableStateAdvice");
    transformers.put(
        named("schedule").and(takesArgument(0, Callable.class)),
        JavaExecutorInstrumentation.class.getName() + "$SetCallableStateAdvice");
    return transformers;
  }

  public static class SetExecuteRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(newTask)) {
        task = newTask;
        ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, newTask, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }

  public static class SetJavaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) ForkJoinTask task) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(task)) {
        ContextStore<ForkJoinTask, State> contextStore =
            InstrumentationContext.get(ForkJoinTask.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, task, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }

  public static class SetSubmitRunnableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(newTask)) {
        task = newTask;
        ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, newTask, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter State state,
        @Advice.Thrown Throwable throwable,
        @Advice.Return Future future) {
      if (state != null && future != null) {
        ContextStore<Future, State> contextStore =
            InstrumentationContext.get(Future.class, State.class);
        contextStore.put(future, state);
      }
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }

  public static class SetCallableStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Callable task) {
      Callable newTask = CallableWrapper.wrapIfNeeded(task);
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(newTask)) {
        task = newTask;
        ContextStore<Callable, State> contextStore =
            InstrumentationContext.get(Callable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, newTask, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter State state,
        @Advice.Thrown Throwable throwable,
        @Advice.Return Future future) {
      if (state != null && future != null) {
        ContextStore<Future, State> contextStore =
            InstrumentationContext.get(Future.class, State.class);
        contextStore.put(future, state);
      }
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }

  public static class SetCallableStateForCallableCollectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Collection<?> submitEnter(
        @Advice.Argument(value = 0, readOnly = false) Collection<? extends Callable<?>> tasks) {
      if (tasks != null) {
        Collection<Callable<?>> wrappedTasks = new ArrayList<>(tasks.size());
        for (Callable<?> task : tasks) {
          if (task != null) {
            Callable newTask = CallableWrapper.wrapIfNeeded(task);
            wrappedTasks.add(newTask);
            ContextStore<Callable, State> contextStore =
                InstrumentationContext.get(Callable.class, State.class);
            ExecutorInstrumentationUtils.setupState(
                contextStore, newTask, Java8BytecodeBridge.currentContext());
          }
        }
        tasks = wrappedTasks;
        return tasks;
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void submitExit(
        @Advice.Enter Collection<? extends Callable<?>> wrappedTasks,
        @Advice.Thrown Throwable throwable) {
      /*
       Note1: invokeAny doesn't return any futures so all we need to do for it
       is to make sure we close all scopes in case of an exception.
       Note2: invokeAll does return futures - but according to its documentation
       it actually only returns after all futures have been completed - i.e. it blocks.
       This means we do not need to setup any hooks on these futures, we just need to clean
       up any continuations in case of an error.
       (according to ExecutorService docs and AbstractExecutorService code)
      */
      if (null != throwable && wrappedTasks != null) {
        for (Callable<?> task : wrappedTasks) {
          if (task != null) {
            ContextStore<Callable, State> contextStore =
                InstrumentationContext.get(Callable.class, State.class);
            State state = contextStore.get(task);
            if (state != null) {
              /*
              Note: this may potentially close somebody else's continuation if we didn't set it
              up in setupState because it was already present before us. This should be safe but
              may lead to non-attributed async work in some very rare cases.
              Alternative is to not close continuation here if we did not set it up in setupState
              but this may potentially lead to memory leaks if callers do not properly handle
              exceptions.
               */
              state.clearParentContext();
            }
          }
        }
      }
    }
  }
}
