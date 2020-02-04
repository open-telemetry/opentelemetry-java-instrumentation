package io.opentelemetry.auto.instrumentation.servlet.filter;

import static io.opentelemetry.auto.instrumentation.servlet.filter.FilterDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.Filter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class FilterInstrumentation extends Instrumenter.Default {
  public FilterInstrumentation() {
    super("servlet-filter");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", packageName + ".FilterDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.Filter")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("doFilter")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        FilterAdvice.class.getName());
  }

  public static class FilterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(@Advice.This final Filter filter) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        // Don't want to generate a new top-level span
        return null;
      }

      final Span span = TRACER.spanBuilder("servlet.filter").startSpan();
      FilterDecorator.DECORATE.afterStart(span);

      // Here we use "this" instead of "the method target" to distinguish abstract filter instances.
      span.setAttribute(MoreTags.RESOURCE_NAME, filter.getClass().getSimpleName() + ".doFilter");

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      final Span span = scope.getSpan();
      FilterDecorator.DECORATE.onError(span, throwable);
      FilterDecorator.DECORATE.beforeFinish(span);
      span.end();
      scope.getScope().close();
    }
  }
}
