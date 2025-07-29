/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.forkjoin.ForkJoinTask;

public class ScalaForkJoinPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("scala.concurrent.forkjoin.ForkJoinPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        // doSubmit is internal method prior to 2.11, and externalPush is the internal method after
        namedOneOf("doSubmit", "externalPush")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetScalaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enterJobSubmit(@Advice.Argument(0) ForkJoinTask<?> task) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, task)) {
        VirtualField<ForkJoinTask<?>, PropagatedContext> virtualField =
            VirtualField.find(ForkJoinTask.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, task);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.Argument(0) ForkJoinTask<?> task,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      VirtualField<ForkJoinTask<?>, PropagatedContext> virtualField =
          VirtualField.find(ForkJoinTask.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable, virtualField, task);
    }
  }
}
