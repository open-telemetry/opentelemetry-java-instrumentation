/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaconcurrent;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8Bridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.forkjoin.ForkJoinTask;

@AutoService(Instrumenter.class)
public final class ScalaForkJoinPoolInstrumentation extends Instrumenter.Default {

  public ScalaForkJoinPoolInstrumentation() {
    super("java_concurrent", "scala_concurrent");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // This might need to be an extendsClass matcher...
    return named("scala.concurrent.forkjoin.ForkJoinPool");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME, State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
    transformers.put(
        named("submit")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
    transformers.put(
        nameMatches("invoke")
            .and(takesArgument(0, named(ScalaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        ScalaForkJoinPoolInstrumentation.class.getName() + "$SetScalaForkJoinStateAdvice");
    return transformers;
  }

  public static class SetScalaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) ForkJoinTask task) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(task)) {
        ContextStore<ForkJoinTask, State> contextStore =
            InstrumentationContext.get(ForkJoinTask.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, task, Java8Bridge.currentContext());
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
