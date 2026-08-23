/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.v1_9_2.XxlJobConstants.XXL_GLUE_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.v1_9_2.XxlJobConstants.XXL_METHOD_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.v1_9_2.XxlJobConstants.XXL_SCRIPT_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2.XxlJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.xxl.job.core.handler.IJobHandler;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.v1_9_2.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.v1_9_2.XxlJobProcessRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

class SimpleJobHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.xxl.job.core.handler.IJobHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.xxl.job.core.handler.IJobHandler"))
        .and(not(namedOneOf(XXL_GLUE_JOB_HANDLER, XXL_SCRIPT_JOB_HANDLER, XXL_METHOD_JOB_HANDLER)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(isPublic()), getClass().getName() + "$ScheduleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static XxlJobHelper.XxlJobScope onSchedule(@Advice.This IJobHandler handler) {
      return helper().startSpan(XxlJobProcessRequest.createSimpleJobRequest(handler));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable XxlJobHelper.XxlJobScope scope) {
      helper().endSpan(scope, result, throwable);
    }
  }
}
