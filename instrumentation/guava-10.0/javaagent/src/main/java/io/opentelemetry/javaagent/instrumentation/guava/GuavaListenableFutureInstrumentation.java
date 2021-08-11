/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.PropagatedContext;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper;
import java.util.concurrent.Executor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class GuavaListenableFutureInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.common.util.concurrent.AbstractFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$AbstractFutureAdvice");
    transformer.applyAdviceToMethod(
        named("addListener").and(ElementMatchers.takesArguments(Runnable.class, Executor.class)),
        this.getClass().getName() + "$AddListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class AbstractFutureAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onConstruction() {
      InstrumentationHelper.initialize();
    }
  }

  @SuppressWarnings("unused")
  public static class AddListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext addListenerEnter(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        task = RunnableWrapper.wrapIfNeeded(task);
        ContextStore<Runnable, PropagatedContext> contextStore =
            InstrumentationContext.get(Runnable.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, contextStore, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addListenerExit(
        @Advice.Enter PropagatedContext propagatedContext, @Advice.Thrown Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable);
    }
  }
}
