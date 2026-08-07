/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ChatModelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.springframework.ai.chat.model.ChatModel"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("call")
            .and(takesArgument(0, named("org.springframework.ai.chat.prompt.Prompt")))
            .and(returns(named("org.springframework.ai.chat.model.ChatResponse"))),
        getClass().getName() + "$CallAdvice");
    transformer.applyAdviceToMethod(
        named("stream")
            .and(takesArgument(0, named("org.springframework.ai.chat.prompt.Prompt")))
            .and(returns(named("reactor.core.publisher.Flux"))),
        getClass().getName() + "$StreamAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Context onEnter(
        @Advice.This Object chatModel, @Advice.Argument(0) Prompt prompt) {
      SpringAiRequest request = SpringAiRequest.create(prompt, chatModel);
      Context parentContext = Context.current();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      SpringAiMessageAttributes.setInputMessages(context, request);
      SpringAiMessageEvents.emitPromptEvents(context, request);
      return context;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Context context,
        @Advice.This Object chatModel,
        @Advice.Argument(0) Prompt prompt,
        @Advice.Return ChatResponse response,
        @Advice.Thrown Throwable throwable) {
      if (context != null) {
        SpringAiRequest request = SpringAiRequest.create(prompt, chatModel);
        SpringAiMessageAttributes.setOutputMessages(context, response, null);
        SpringAiMessageEvents.emitResponseEvents(context, request, response, null);
        instrumenter().end(context, request, response, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class StreamAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Flux<ChatResponse> onExit(
        @Advice.This Object chatModel,
        @Advice.Argument(0) Prompt prompt,
        @Advice.Return Flux<ChatResponse> publisher) {
      if (publisher == null) {
        return publisher;
      }
      return SpringAiStreamTracing.wrap(publisher, SpringAiRequest.create(prompt, chatModel));
    }
  }
}
