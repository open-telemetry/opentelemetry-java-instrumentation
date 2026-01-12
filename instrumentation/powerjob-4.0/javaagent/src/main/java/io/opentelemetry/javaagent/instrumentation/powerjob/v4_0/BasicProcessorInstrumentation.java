/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.core.processor.sdk.BasicProcessor;

public class BasicProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("tech.powerjob.worker.core.processor.sdk.BasicProcessor"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process")
            .and(isPublic())
            .and(
                takesArguments(1)
                    .and(
                        takesArgument(
                            0, named("tech.powerjob.worker.core.processor.TaskContext")))),
        BasicProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {

    public static class AdviceScope {
      private final PowerJobProcessRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(PowerJobProcessRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(BasicProcessor handler, TaskContext taskContext) {
        Context parentContext = Context.current();
        PowerJobProcessRequest request =
            PowerJobProcessRequest.createRequest(
                taskContext.getJobId(),
                handler,
                "process",
                taskContext.getJobParams(),
                taskContext.getInstanceParams());

        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void end(ProcessResult result, Throwable throwable) {
        scope.close();
        instrumenter().end(context, request, result, throwable);
      }
    }

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onSchedule(
        @Advice.This BasicProcessor handler, @Advice.Argument(0) TaskContext taskContext) {
      return AdviceScope.start(handler, taskContext);
    }

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return ProcessResult result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(result, throwable);
      }
    }
  }
}
