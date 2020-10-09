/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import com.google.common.util.concurrent.AbstractFuture;
import io.grpc.Context;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import java.util.concurrent.Executor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

@AutoService(Instrumenter.class)
public class ListenableFutureInstrumentation extends Instrumenter.Default {

  public ListenableFutureInstrumentation() {
    super("guava");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.common.util.concurrent.AbstractFuture");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(Runnable.class.getName(), State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("addListener").and(ElementMatchers.takesArguments(Runnable.class, Executor.class)),
        ListenableFutureInstrumentation.class.getName() + "$AddListenerAdvice");
  }

  public static class AddListenerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State addListenerEnter(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      final Context context = Context.current();
      final Runnable newTask = RunnableWrapper.wrapIfNeeded(task);
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(newTask)) {
        task = newTask;
        final ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(contextStore, newTask, context);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addListenerExit(
        @Advice.Enter final State state, @Advice.Thrown final Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }

    private static void muzzleCheck(final AbstractFuture<?> future) {
      future.addListener(null, null);
    }
  }
}
