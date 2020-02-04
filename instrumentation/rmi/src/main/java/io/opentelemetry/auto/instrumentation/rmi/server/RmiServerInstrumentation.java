package io.opentelemetry.auto.instrumentation.rmi.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.auto.instrumentation.rmi.server.RmiServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.rmi.server.RmiServerDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RmiServerInstrumentation extends Instrumenter.Default {

  public RmiServerInstrumentation() {
    super("rmi", "rmi-server");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.ServerDecorator",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      packageName + ".RmiServerDecorator"
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.rmi.server.RemoteServer")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(not(isStatic())), getClass().getName() + "$ServerAdvice");
  }

  public static class ServerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = true)
    public static SpanWithScope onEnter(
        @Advice.This final Object thiz, @Advice.Origin final Method method) {
      final SpanContext context = THREAD_LOCAL_CONTEXT.getAndResetContext();

      final Span.Builder spanBuilder = TRACER.spanBuilder("rmi.request");
      if (context != null) {
        spanBuilder.setParent(context);
      }
      final Span span = spanBuilder.startSpan();
      span.setAttribute(MoreTags.RESOURCE_NAME, DECORATE.spanNameForMethod(method));
      span.setAttribute("span.origin.type", thiz.getClass().getCanonicalName());

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
