/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import static io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2.XxlJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobHelper;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodJobHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.xxl.job.core.handler.impl.MethodJobHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(isPublic()),
        MethodJobHandlerInstrumentation.class.getName() + "$ScheduleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static XxlJobHelper.XxlJobScope onSchedule(
        @Advice.FieldValue("target") Object target, @Advice.FieldValue("method") Method method) {
      return helper().startSpan(XxlJobProcessRequest.createMethodJobRequest(target, method));
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
