/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.tool;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.chat.messages.AssistantMessage;

@AutoService(TypeInstrumentation.class)
public class DefaultToolCallingManagerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.ai.model.tool.DefaultToolCallingManager");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.ai.model.tool.DefaultToolCallingManager");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("executeToolCall"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("org.springframework.ai.chat.messages.AssistantMessage"))),
        this.getClass().getName() + "$ExecuteToolCallAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteToolCallAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void executeToolCallEnter(
        @Advice.Argument(1) AssistantMessage assistantMessage,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      context = Context.current();

      if (assistantMessage != null && assistantMessage.getToolCalls() != null) {
        Map<String, String> toolNameToIdMap = new HashMap<>();

        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
          if (toolCall.id() != null && toolCall.name() != null) {
            toolNameToIdMap.put(toolCall.name(), toolCall.id());
          }
        }

        // store tool call ids map to context
        if (!toolNameToIdMap.isEmpty()) {
          context = ToolCallContext.storeToolCalls(context, toolNameToIdMap);
        }
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void executeToolCallExit(@Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
    }
  }
}
