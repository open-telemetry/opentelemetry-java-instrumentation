/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.v2_3_0.XxlJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodJobHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.xxl.job.core.handler.impl.MethodJobHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(isPublic()).and(takesNoArguments()),
        MethodJobHandlerInstrumentation.class.getName() + "$ScheduleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onSchedule(
        @Advice.FieldValue("target") Object target,
        @Advice.FieldValue("method") Method method,
        @Advice.Local("otelRequest") XxlJobProcessRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      request = XxlJobProcessRequest.createMethodJobRequest(target, method);
      context = helper().startSpan(parentContext, request);
      if (context == null) {
        return;
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") XxlJobProcessRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      helper().stopSpan(request, throwable, scope, context);
    }
  }
}
