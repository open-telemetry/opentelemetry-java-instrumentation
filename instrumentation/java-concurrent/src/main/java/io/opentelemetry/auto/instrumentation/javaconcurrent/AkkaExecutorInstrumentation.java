/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.javaconcurrent;

import static io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.AdviceUtils.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.forkjoin.ForkJoinTask;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.State;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class AkkaExecutorInstrumentation extends AbstractExecutorInstrumentation {

  public AkkaExecutorInstrumentation() {
    super(EXEC_NAME + ".akka_fork_join");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME, State.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("execute")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaExecutorInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    transformers.put(
        named("submit")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaExecutorInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    transformers.put(
        nameMatches("invoke")
            .and(takesArgument(0, named(AkkaForkJoinTaskInstrumentation.TASK_CLASS_NAME))),
        AkkaExecutorInstrumentation.class.getName() + "$SetAkkaForkJoinStateAdvice");
    return transformers;
  }

  public static class SetAkkaForkJoinStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Argument(value = 0, readOnly = false) final ForkJoinTask task) {
      final Span span = TRACER.getCurrentSpan();
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(task, executor)) {
        final ContextStore<ForkJoinTask, State> contextStore =
            InstrumentationContext.get(ForkJoinTask.class, State.class);
        return ExecutorInstrumentationUtils.setupState(contextStore, task, span);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitJobSubmit(
        @Advice.This final Executor executor,
        @Advice.Enter final State state,
        @Advice.Thrown final Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }
}
