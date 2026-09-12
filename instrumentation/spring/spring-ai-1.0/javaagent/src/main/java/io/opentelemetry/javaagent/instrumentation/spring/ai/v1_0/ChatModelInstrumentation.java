/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.v1_0.SpringAiSingletons.instrumenter;
import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.logging.Logger;
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
      private static final Logger logger = Logger.getLogger(AdviceScope.class.getName());
      private final Context context;
      private final Scope scope;
      private final SpringAiRequest request;

      private AdviceScope(Context context, SpringAiRequest request) {
        this.context = context;
        this.scope = context.makeCurrent();
        this.request = request;
      }

      @Nullable
      public static AdviceScope start(Object chatModel, Prompt prompt, boolean streaming) {
        SpringAiRequest request = SpringAiRequest.create(prompt, chatModel, streaming);
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        try {
          SpringAiMessageEvents.emitPromptEvents(context, request);
        } catch (Throwable t) {
          logger.log(FINE, "Failed to emit Spring AI prompt events", t);
        }
        return new AdviceScope(context, request);
      }

      public void end(@Nullable ChatResponse response, @Nullable Throwable throwable) {
        scope.close();
        try {
          SpringAiMessageEvents.emitResponseEvents(context, request, response, null);
        } catch (Throwable t) {
          logger.log(FINE, "Failed to emit Spring AI response events", t);
        }
        instrumenter()
            .end(
                context,
                request,
                response == null ? null : new SpringAiResponse(response, null),
                throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Object chatModel, @Advice.Argument(0) Prompt prompt) {
      return AdviceScope.start(chatModel, prompt, false);
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
        return new StreamAdviceScope(callDepth, nested);
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
        CallAdvice.AdviceScope callAdviceScope =
            CallAdvice.AdviceScope.start(chatModel, prompt, true);
        if (callAdviceScope != null) {
          callAdviceScope.end(null, throwable);
        }
        return publisher;
      }
      if (publisher == null) {
        return publisher;
      }
      return SpringAiStreamTracing.wrap(publisher, SpringAiRequest.create(prompt, chatModel, true));
    }
  }
}
