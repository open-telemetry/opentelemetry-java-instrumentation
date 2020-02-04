package io.opentelemetry.auto.instrumentation.jsp;

import static io.opentelemetry.auto.instrumentation.jsp.JSPDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jsp.JSPDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JSPInstrumentation extends Instrumenter.Default {

  public JSPInstrumentation() {
    super("jsp", "jsp-render");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.jsp.HttpJspPage")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", packageName + ".JSPDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("_jspService")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(isPublic()),
        JSPInstrumentation.class.getName() + "$HttpJspPageAdvice");
  }

  public static class HttpJspPageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.This final Object obj, @Advice.Argument(0) final HttpServletRequest req) {
      final Span span = TRACER.spanBuilder("jsp.render").startSpan();
      span.setAttribute("span.origin.type", obj.getClass().getSimpleName());
      span.setAttribute("servlet.context", req.getContextPath());
      DECORATE.afterStart(span);
      DECORATE.onRender(span, req);
      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.getScope().close();
    }
  }
}
