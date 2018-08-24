package datadog.trace.instrumentation.springwebflux;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HandlerFunctionAdapterInstrumentation extends Instrumenter.Default {

  public HandlerFunctionAdapterInstrumentation() {
    super("spring-webflux");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter");
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArguments(2)),
        HandlerFunctionAdapterAdvice.class.getName());
  }

  public static class HandlerFunctionAdapterAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void recordHandlerFunctionTag(
        @Advice.Thrown final Throwable throwable, @Advice.Argument(1) final Object handler) {

      final Span activeSpan = GlobalTracer.get().activeSpan();

      if (activeSpan != null && handler != null) {
        activeSpan.setTag("handler_function.class", handler.getClass().getName());
        if (throwable != null) {
          Tags.ERROR.set(activeSpan, true);
          activeSpan.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
      }
    }
  }
}
