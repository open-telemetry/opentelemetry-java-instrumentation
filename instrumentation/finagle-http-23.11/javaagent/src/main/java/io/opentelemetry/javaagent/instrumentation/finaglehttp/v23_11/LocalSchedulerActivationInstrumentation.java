/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LocalSchedulerActivationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.concurrent.LocalScheduler$Activation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("submit").and(takesArguments(Runnable.class)),
        this.getClass().getName() + "$WrapRunnableAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapRunnableAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static Runnable wrap(@Advice.Argument(0) Runnable task) {
      if (task == null) {
        return null;
      }

      Context context = Java8BytecodeBridge.currentContext();
      return ContextPropagatingRunnable.propagateContext(task, context);
    }
  }
}
