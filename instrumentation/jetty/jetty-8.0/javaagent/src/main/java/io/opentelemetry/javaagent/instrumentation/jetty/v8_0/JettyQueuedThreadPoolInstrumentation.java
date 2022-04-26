/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JettyQueuedThreadPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.util.thread.QueuedThreadPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch").and(takesArguments(1)).and(takesArgument(0, Runnable.class)),
        this.getClass().getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) Runnable task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        VirtualField<Runnable, PropagatedContext> virtualField =
            VirtualField.find(Runnable.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Enter PropagatedContext propagatedContext, @Advice.Thrown Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable);
    }
  }
}
