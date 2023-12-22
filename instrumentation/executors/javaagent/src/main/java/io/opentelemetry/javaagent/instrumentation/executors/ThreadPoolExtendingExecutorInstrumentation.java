/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.instrumentation.executors.ExecutorMatchers.executorNameMatcher;
import static io.opentelemetry.javaagent.instrumentation.executors.ExecutorMatchers.isThreadPoolExecutor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ThreadPoolExtendingExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return executorNameMatcher().and(isThreadPoolExecutor());
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("beforeExecute").and(takesArgument(1, Runnable.class)),
        this.getClass().getName() + "$BeforeExecuteAdvice");
    transformer.applyAdviceToMethod(
        named("afterExecute").and(takesArgument(0, Runnable.class)),
        this.getClass().getName() + "$AfterExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class BeforeExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 1, readOnly = false) Runnable runnable) {
      if (runnable instanceof ContextPropagatingRunnable) {
        runnable = ((ContextPropagatingRunnable) runnable).unwrap();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AfterExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
      if (runnable instanceof ContextPropagatingRunnable) {
        runnable = ((ContextPropagatingRunnable) runnable).unwrap();
      }
    }
  }
}
