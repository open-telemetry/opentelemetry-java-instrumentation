/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springai.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.springai.v1_0.SpringAiSingletons.shouldSuppressNestedChatModelInstrumentation;
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
        if (callDepth.getAndIncrement() > 0
            || shouldSuppressNestedChatModelInstrumentation(Context.current())) {
          return new AdviceScope(callDepth, null, null, null);
        }

        SpringAiRequest request = null;
        Context context = null;
        Scope scope = null;
        boolean completed = false;
        try {
          request = SpringAiRequest.create(prompt, chatModel);
          Context parentContext = Context.current();
          if (!instrumenter().shouldStart(parentContext, request)) {
            AdviceScope adviceScope = new AdviceScope(callDepth, null, null, null);
            completed = true;
            return adviceScope;
          }
          context = instrumenter().start(parentContext, request);
          scope = context.makeCurrent();
          SpringAiMessageAttributes.setInputMessages(context, request);
          SpringAiMessageEvents.emitPromptEvents(context, request);
          AdviceScope adviceScope = new AdviceScope(callDepth, context, scope, request);
          completed = true;
          return adviceScope;
        } finally {
          if (!completed) {
            cleanupAfterStartFailure(callDepth, context, scope, request);
          }
        }
      }

      private static void cleanupAfterStartFailure(
          CallDepth callDepth,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable SpringAiRequest request) {
        try {
          if (scope != null) {
            scope.close();
          }
        } finally {
          try {
            if (context != null && request != null) {
              instrumenter().end(context, request, null, null);
            }
          } finally {
            callDepth.decrementAndGet();
          }
        }
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
    public static class StreamAdviceScope {
      private final CallDepth callDepth;
      private final boolean suppressed;

      private StreamAdviceScope(CallDepth callDepth, boolean suppressed) {
        this.callDepth = callDepth;
        this.suppressed = suppressed;
      }

      public static StreamAdviceScope start() {
        CallDepth callDepth = CallDepth.forClass(ChatModel.class);
        boolean nested = callDepth.getAndIncrement() > 0;
        return new StreamAdviceScope(
            callDepth, nested || shouldSuppressNestedChatModelInstrumentation(Context.current()));
      }

      public boolean shouldSuppress() {
        return callDepth.decrementAndGet() > 0 || suppressed;
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static StreamAdviceScope onEnter() {
      return StreamAdviceScope.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Flux<ChatResponse> onExit(
        @Advice.This Object chatModel,
        @Advice.Argument(0) Prompt prompt,
        @Advice.Return @Nullable Flux<ChatResponse> publisher,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable StreamAdviceScope adviceScope) {
      if (adviceScope == null || adviceScope.shouldSuppress()) {
        return publisher;
      }
      if (throwable != null) {
        CallAdvice.AdviceScope callAdviceScope = CallAdvice.AdviceScope.start(chatModel, prompt);
        callAdviceScope.end(null, throwable);
        return publisher;
      }
      if (publisher == null) {
        return publisher;
      }
      return SpringAiStreamTracing.wrap(publisher, SpringAiRequest.create(prompt, chatModel));
    }
  }
}
