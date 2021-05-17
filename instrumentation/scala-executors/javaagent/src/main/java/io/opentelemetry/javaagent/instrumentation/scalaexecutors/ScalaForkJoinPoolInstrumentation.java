/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors;

import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.forkjoin.ForkJoinTask;

public class ScalaForkJoinPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // This might need to be an extendsClass matcher...
    return named("scala.concurrent.forkjoin.ForkJoinPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
    transformer.applyAdviceToMethod(
        named("submit")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
    transformer.applyAdviceToMethod(
        nameMatches("invoke")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
  }

  public static class SetScalaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) ForkJoinTask<?> task) {
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
}
