package io.opentelemetry.auto.instrumentation.jaxrs2;

import static io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.hasSuperMethod;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.AsyncResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAnnotationsInstrumentation extends Instrumenter.Default {

  private static final String JAX_ENDPOINT_OPERATION_NAME = "jax-rs.request";

  public JaxRsAnnotationsInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-annotations");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.ws.rs.container.AsyncResponse", Span.class.getName());
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .or(safeHasSuperType(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path"))))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        named("javax.ws.rs.Path")
                            .or(named("javax.ws.rs.DELETE"))
                            .or(named("javax.ws.rs.GET"))
                            .or(named("javax.ws.rs.HEAD"))
                            .or(named("javax.ws.rs.OPTIONS"))
                            .or(named("javax.ws.rs.POST"))
                            .or(named("javax.ws.rs.PUT"))))),
        JaxRsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope nameSpan(
        @Advice.This final Object target, @Advice.Origin final Method method) {
      // Rename the parent span according to the path represented by these annotations.
      final Span parent = TRACER.getCurrentSpan();

      final Span span = TRACER.spanBuilder(JAX_ENDPOINT_OPERATION_NAME).startSpan();
      DECORATE.onJaxRsSpan(span, parent, target.getClass(), method);
      DECORATE.afterStart(span);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanAndScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.AllArguments final Object[] args) {
      final Span span = spanAndScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanAndScope.closeScope();
        return;
      }

      AsyncResponse asyncResponse = null;
      for (final Object arg : args) {
        if (arg instanceof AsyncResponse) {
          asyncResponse = (AsyncResponse) arg;
          break;
        }
      }
      if (asyncResponse != null && asyncResponse.isSuspended()) {
        InstrumentationContext.get(AsyncResponse.class, Span.class).put(asyncResponse, span);
      } else {
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanAndScope.closeScope();
    }
  }
}
