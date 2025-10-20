package io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.spring.ai.openai.v1_0.SpringAiOpenaiSingletons.TELEMETRY;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

@AutoService(TypeInstrumentation.class)
public class OpenAiApiInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.ai.openai.api.OpenAiApi");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.ai.openai.api.OpenAiApi");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("chatCompletionEntity")).and(takesArguments(2))
            .and(takesArgument(0, named("org.springframework.ai.openai.api.OpenAiApi$ChatCompletionRequest")))
            .and(returns(named("org.springframework.http.ResponseEntity"))),
        this.getClass().getName() + "$CallAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("chatCompletionStream")).and(takesArguments(2))
            .and(takesArgument(0, named("org.springframework.ai.openai.api.OpenAiApi$ChatCompletionRequest")))
            .and(returns(named("reactor.core.publisher.Flux"))),
        this.getClass().getName() + "$StreamAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void callEnter(
        @Advice.Argument(0) ChatCompletionRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = Context.current();
      if (!TELEMETRY.chatCompletionInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = TELEMETRY.chatCompletionInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void callExit(
        @Advice.Argument(0) ChatCompletionRequest request,
        @Advice.Return ResponseEntity<ChatCompletion> response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      TELEMETRY.chatCompletionInstrumenter()
          .end(context, request, response.hasBody() ? response.getBody() : null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class StreamAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void streamEnter(
        @Advice.Argument(0) ChatCompletionRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelStreamListener") ChatModelStreamListener streamListener) {
      context = Context.current();
      
      if (TELEMETRY.chatCompletionInstrumenter().shouldStart(context, request)) {
        context = TELEMETRY.chatCompletionInstrumenter().start(context, request);
        streamListener = new ChatModelStreamListener(
            context, request, TELEMETRY.chatCompletionInstrumenter(),
            TELEMETRY.messageCaptureOptions(), true);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void streamExit(
        @Advice.Argument(0) ChatCompletionRequest request,
        @Advice.Return(readOnly = false) Flux<ChatCompletionChunk> response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelStreamListener") ChatModelStreamListener streamListener) {

      if (throwable != null) {
        // In case of exception, directly call end
        TELEMETRY.chatCompletionInstrumenter().end(context, request, null, throwable);
        return;
      }

      if (streamListener != null) {
        // Wrap the response to integrate the stream listener
        response = ChatModelStreamWrapper.wrap(response, streamListener, context);
      }
    }
  }
}
