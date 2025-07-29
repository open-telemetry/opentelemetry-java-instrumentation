/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
    public static PropagatedContext addListenerEnter(@Advice.Argument(0) Runnable task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        VirtualField<Runnable, PropagatedContext> virtualField =
            VirtualField.find(Runnable.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addListenerExit(
        @Advice.Argument(0) Runnable task,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      VirtualField<Runnable, PropagatedContext> virtualField =
          VirtualField.find(Runnable.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable, virtualField, task);
    }
  }
}
