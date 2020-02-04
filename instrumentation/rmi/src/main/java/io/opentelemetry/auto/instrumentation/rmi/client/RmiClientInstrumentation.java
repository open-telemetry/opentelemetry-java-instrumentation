package io.opentelemetry.auto.instrumentation.rmi.client;

import static io.opentelemetry.auto.instrumentation.rmi.client.RmiClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.rmi.client.RmiClientDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RmiClientInstrumentation extends Instrumenter.Default {

  public RmiClientInstrumentation() {
    super("rmi", "rmi-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("sun.rmi.server.UnicastRef")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".RmiClientDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("invoke"))
            .and(takesArgument(0, named("java.rmi.Remote")))
            .and(takesArgument(1, named("java.lang.reflect.Method"))),
        getClass().getName() + "$RmiClientAdvice");
  }

  public static class RmiClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(@Advice.Argument(value = 1) final Method method) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        return null;
      }
      final Span span = TRACER.spanBuilder("rmi.invoke").startSpan();
      span.setAttribute(MoreTags.RESOURCE_NAME, DECORATE.spanNameForMethod(method));
      span.setAttribute("span.origin.type", method.getDeclaringClass().getCanonicalName());

      DECORATE.afterStart(span);
      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      span.end();
      spanWithScope.getScope().close();
    }
  }
}
