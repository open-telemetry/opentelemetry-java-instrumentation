package io.opentelemetry.auto.instrumentation.springweb;

import static io.opentelemetry.auto.instrumentation.springweb.SpringWebHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.springweb.SpringWebHttpServerDecorator.DECORATE_RENDER;
import static io.opentelemetry.auto.instrumentation.springweb.SpringWebHttpServerDecorator.TRACER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

@AutoService(Instrumenter.class)
public final class DispatcherServletInstrumentation extends Instrumenter.Default {

  public DispatcherServletInstrumentation() {
    super("spring-web");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.servlet.DispatcherServlet");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ServerDecorator",
      "io.opentelemetry.auto.decorator.HttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator",
      packageName + ".SpringWebHttpServerDecorator$1",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(named("render"))
            .and(takesArgument(0, named("org.springframework.web.servlet.ModelAndView"))),
        DispatcherServletInstrumentation.class.getName() + "$DispatcherAdvice");
    transformers.put(
        isMethod()
            .and(isProtected())
            .and(nameStartsWith("processHandlerException"))
            .and(takesArgument(3, Exception.class)),
        DispatcherServletInstrumentation.class.getName() + "$ErrorHandlerAdvice");
    return transformers;
  }

  public static class DispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair onEnter(@Advice.Argument(0) final ModelAndView mv) {
      final Span span = TRACER.spanBuilder("response.render").startSpan();
      DECORATE_RENDER.afterStart(span);
      DECORATE_RENDER.onRender(span, mv);
      return new SpanScopePair(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanScopePair spanAndScope, @Advice.Thrown final Throwable throwable) {
      final Span span = spanAndScope.getSpan();
      DECORATE_RENDER.onError(span, throwable);
      DECORATE_RENDER.beforeFinish(span);
      span.end();
      spanAndScope.getScope().close();
    }

    // Make this advice match consistently with HandlerAdapterInstrumentation
    private void muzzleCheck(final HandlerMethod method) {
      method.getMethod();
    }
  }

  public static class ErrorHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameResource(@Advice.Argument(3) final Exception exception) {
      final Span span = TRACER.getCurrentSpan();
      if (span.getContext().isValid() && exception != null) {
        // We want to capture the stacktrace, but that doesn't mean it should be an error.
        // We rely on a decorator to set the error state based on response code. (5xx -> error)
        DECORATE.addThrowable(span, exception);
      }
    }
  }
}
