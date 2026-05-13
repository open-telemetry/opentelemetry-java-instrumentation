/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TaskQueueInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.vertx.core.impl.TaskQueue",
        "io.vertx.core.impl.OrderedExecutorFactory$OrderedExecutor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(0, Runnable.class)),
        getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Advice.AssignReturned.ToArguments(
        @Advice.AssignReturned.ToArguments.ToArgument(value = 0, index = 0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(0) Runnable task) {
      // Attach context to the task being executed and prevent further context propagation in this
      // method. This method places the task into queue and executes a runnable that starts
      // consuming tasks from the queue. Since we already attached context to the task, we don't
      // want to propagate context into the runnable that consumes tasks from the queue.
      Context context = Java8BytecodeBridge.currentContext();
      return new Object[] {
        ContextPropagatingRunnable.propagateContext(task, context),
        Java8BytecodeBridge.rootContext().makeCurrent()
      };
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter Object[] enterArguments) {
      Scope scope = (Scope) enterArguments[1];
      if (scope != null) {
        scope.close();
      }
    }
  }
}
