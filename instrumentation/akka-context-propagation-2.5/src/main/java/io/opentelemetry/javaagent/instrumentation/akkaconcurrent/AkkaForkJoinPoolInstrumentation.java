/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaconcurrent;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.forkjoin.ForkJoinTask;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class AkkaForkJoinPoolInstrumentation extends Instrumenter.Default {

  public AkkaForkJoinPoolInstrumentation() {
    super("akka_context_propagation");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // This might need to be an extendsClass matcher...
    return named("akka.dispatch.forkjoin.ForkJoinPool");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME, State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaForkJoinPoolInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    transformers.put(
        named("submit")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaForkJoinPoolInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    transformers.put(
        nameMatches("invoke")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaForkJoinPoolInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    return transformers;
  }

  public static class SetAkkaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.Argument(value = 0, readOnly = false) ForkJoinTask task) {
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
