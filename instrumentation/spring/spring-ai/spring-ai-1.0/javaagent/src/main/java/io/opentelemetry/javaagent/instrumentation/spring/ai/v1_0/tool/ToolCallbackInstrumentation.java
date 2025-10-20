/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.TELEMETRY;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.tool.ToolCallback;

@AutoService(TypeInstrumentation.class)
public class ToolCallbackInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.ai.tool.ToolCallback");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.springframework.ai.tool.ToolCallback"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("call")).and(takesArguments(2))
        .and(takesArgument(0, named("java.lang.String")))
        .and(returns(named("java.lang.String"))),
        this.getClass().getName() + "$CallAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void callEnter(
        @Advice.This ToolCallback toolCallback,
        @Advice.Argument(0) String toolInput,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("toolCallRequest") ToolCallRequest request) {
      context = Context.current();
      
      // get tool call id from context
      String toolCallId = ToolCallContext.getToolCallId(context, toolCallback.getToolDefinition().name());
      request = ToolCallRequest.create(toolInput, toolCallId, toolCallback.getToolDefinition());
      
      if (TELEMETRY.toolCallInstrumenter().shouldStart(context, request)) {
        context = TELEMETRY.toolCallInstrumenter().start(context, request);
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void callExit(
        @Advice.Return String result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("toolCallRequest") ToolCallRequest request) {
      if (scope == null) {
        return;
      }
      scope.close();
      TELEMETRY.toolCallInstrumenter().end(context, request, result, throwable);
    }
  }
}
