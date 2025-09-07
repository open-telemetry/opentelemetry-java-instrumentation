/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.XXL_GLUE_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.XXL_METHOD_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.XXL_SCRIPT_JOB_HANDLER;
import static io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0.XxlJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.xxl.job.core.handler.IJobHandler;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class SimpleJobHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.xxl.job.core.handler.IJobHandler"))
        .and(not(namedOneOf(XXL_GLUE_JOB_HANDLER, XXL_SCRIPT_JOB_HANDLER, XXL_METHOD_JOB_HANDLER)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(isPublic()).and(takesNoArguments()),
        SimpleJobHandlerInstrumentation.class.getName() + "$ScheduleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static XxlJobHelper.XxlJobScope onSchedule(@Advice.This IJobHandler handler) {
      return helper().startSpan(XxlJobProcessRequest.createSimpleJobRequest(handler));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable Object result,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable XxlJobHelper.XxlJobScope scope) {
      helper().endSpan(scope, result, throwable);
    }
  }
}
