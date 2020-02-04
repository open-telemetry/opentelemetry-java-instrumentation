package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.instrumentation.jdbc.DataSourceDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jdbc.DataSourceDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.sql.DataSource;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class DataSourceInstrumentation extends Instrumenter.Default {
  public DataSourceInstrumentation() {
    super("jdbc-datasource");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", packageName + ".DataSourceDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.sql.DataSource")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(named("getConnection"), GetConnectionAdvice.class.getName());
  }

  public static class GetConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(@Advice.This final DataSource ds) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        // Don't want to generate a new top-level span
        return null;
      }

      final Span span = TRACER.spanBuilder("database.connection").startSpan();
      DECORATE.afterStart(span);

      span.setAttribute(MoreTags.RESOURCE_NAME, ds.getClass().getSimpleName() + ".getConnection");

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanAndScope, @Advice.Thrown final Throwable throwable) {
      if (spanAndScope == null) {
        return;
      }
      final Span span = spanAndScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanAndScope.getScope().close();
    }
  }
}
