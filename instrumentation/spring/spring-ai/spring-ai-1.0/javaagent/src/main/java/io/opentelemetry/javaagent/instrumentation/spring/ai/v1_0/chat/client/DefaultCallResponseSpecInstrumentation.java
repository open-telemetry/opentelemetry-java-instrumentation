/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.chat.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.TELEMETRY;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;

@AutoService(TypeInstrumentation.class)
public class DefaultCallResponseSpecInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "org.springframework.ai.chat.client.DefaultChatClient$DefaultCallResponseSpec");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.ai.chat.client.DefaultChatClient$DefaultCallResponseSpec");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("doGetObservableChatClientResponse"))
            .and(takesArguments(2))
            .and(isPrivate())
            .and(takesArgument(0, named("org.springframework.ai.chat.client.ChatClientRequest"))),
        this.getClass().getName() + "$DoGetObservableChatClientResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class DoGetObservableChatClientResponseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void doGetObservableChatClientResponseEnter(
        @Advice.Argument(0) ChatClientRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = Context.current();

      if (TELEMETRY.chatClientInstrumenter().shouldStart(context, request)) {
        context = TELEMETRY.chatClientInstrumenter().start(context, request);
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void doGetObservableChatClientResponseExit(
        @Advice.Argument(0) ChatClientRequest request,
        @Advice.Return ChatClientResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      TELEMETRY.chatClientInstrumenter().end(context, request, response, throwable);
    }
  }
}
