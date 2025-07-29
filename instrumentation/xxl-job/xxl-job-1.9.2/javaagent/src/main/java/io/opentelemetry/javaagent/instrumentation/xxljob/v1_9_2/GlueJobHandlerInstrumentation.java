/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2.XxlJobSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.xxl.job.core.handler.IJobHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class GlueJobHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.xxl.job.core.handler.impl.GlueJobHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(isPublic()).and(takesArguments(String.class)),
        GlueJobHandlerInstrumentation.class.getName() + "$ScheduleAdvice");
  }

  @SuppressWarnings("unused")
  public static class ScheduleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onSchedule(
        @Advice.FieldValue("jobHandler") IJobHandler handler,
        @Advice.Local("otelRequest") XxlJobProcessRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      request = XxlJobProcessRequest.createGlueJobRequest(handler);
      context = helper().startSpan(parentContext, request);
      if (context == null) {
        return;
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") XxlJobProcessRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      helper().stopSpan(result, request, throwable, scope, context);
    }
  }
}
