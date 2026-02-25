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
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

@AutoService(TypeInstrumentation.class)
public class DefaultStreamResponseSpecInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(
        "org.springframework.ai.chat.client.DefaultChatClient$DefaultStreamResponseSpec");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.ai.chat.client.DefaultChatClient$DefaultStreamResponseSpec");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("doGetObservableFluxChatResponse"))
            .and(takesArguments(1))
            .and(isPrivate())
            .and(takesArgument(0, named("org.springframework.ai.chat.client.ChatClientRequest"))),
        this.getClass().getName() + "$DoGetObservableFluxChatResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class DoGetObservableFluxChatResponseAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void doGetObservableFluxChatResponseEnter(
        @Advice.Argument(0) ChatClientRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelStreamListener") ChatClientStreamListener streamListener) {
      context = Context.current();

      if (TELEMETRY.chatClientInstrumenter().shouldStart(context, request)) {
        context = TELEMETRY.chatClientInstrumenter().start(context, request);
        streamListener =
            new ChatClientStreamListener(
                context,
                request,
                TELEMETRY.chatClientInstrumenter(),
                TELEMETRY.messageCaptureOptions(),
                true);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void doGetObservableFluxChatResponseExit(
        @Advice.Argument(0) ChatClientRequest request,
        @Advice.Return(readOnly = false) Flux<ChatClientResponse> response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelStreamListener") ChatClientStreamListener streamListener) {

      if (throwable != null) {
        // In case of exception, directly call end
        TELEMETRY.chatClientInstrumenter().end(context, request, null, throwable);
        return;
      }

      if (streamListener != null) {
        // Wrap the response to integrate the stream listener
        response = ChatClientStreamWrapper.wrap(response, streamListener, context);
      }
    }
  }
}
