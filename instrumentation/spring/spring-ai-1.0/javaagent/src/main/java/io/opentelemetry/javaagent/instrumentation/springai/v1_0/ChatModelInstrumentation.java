/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ChatModelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.ai.chat.model.ChatModel");
  }

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

    public static class AdviceScope {
      private final CallDepth callDepth;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      @Nullable private final SpringAiRequest request;

      private AdviceScope(
          CallDepth callDepth,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable SpringAiRequest request) {
        this.callDepth = callDepth;
        this.context = context;
        this.scope = scope;
        this.request = request;
      }

      public static AdviceScope start(Object chatModel, Prompt prompt) {
        CallDepth callDepth = CallDepth.forClass(ChatModel.class);
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
        }
        SpringAiRequest request = SpringAiRequest.create(prompt, chatModel);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context context = instrumenter().start(parentContext, request);
        AdviceScope adviceScope =
            new AdviceScope(callDepth, context, context.makeCurrent(), request);
        SpringAiMessageAttributes.setInputMessages(context, request);
        SpringAiMessageEvents.emitPromptEvents(context, request);
        return adviceScope;
      }

      public void end(@Nullable ChatResponse response, @Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0
            || scope == null
            || context == null
            || request == null) {
          return;
        }
        try {
          scope.close();
        } finally {
          SpringAiMessageAttributes.setOutputMessages(context, response, null);
          SpringAiMessageEvents.emitResponseEvents(context, request, response, null);
          instrumenter().end(context, request, response, throwable);
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Object chatModel, @Advice.Argument(0) Prompt prompt) {
      return AdviceScope.start(chatModel, prompt);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable ChatResponse response,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(response, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class StreamAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallDepth onEnter() {
      CallDepth callDepth = CallDepth.forClass(ChatModel.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Flux<ChatResponse> onExit(
        @Advice.This Object chatModel,
        @Advice.Argument(0) Prompt prompt,
        @Advice.Return @Nullable Flux<ChatResponse> publisher,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable CallDepth callDepth) {
      if (callDepth == null || callDepth.decrementAndGet() > 0) {
        return publisher;
      }
      if (throwable != null) {
        CallAdvice.AdviceScope adviceScope = CallAdvice.AdviceScope.start(chatModel, prompt);
        adviceScope.end(null, throwable);
        return publisher;
      }
      if (publisher == null) {
        return publisher;
      }
      return SpringAiStreamTracing.wrap(publisher, SpringAiRequest.create(prompt, chatModel));
    }
  }
}
