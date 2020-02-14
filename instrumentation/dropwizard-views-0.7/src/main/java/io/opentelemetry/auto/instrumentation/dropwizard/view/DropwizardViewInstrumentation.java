package io.opentelemetry.auto.instrumentation.dropwizard.view;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.dropwizard.views.View;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class DropwizardViewInstrumentation extends Instrumenter.Default {

  public DropwizardViewInstrumentation() {
    super("dropwizard", "dropwizard-view");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.dropwizard.views.ViewRenderer")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", getClass().getName() + "$RenderAdvice"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("render"))
            .and(takesArgument(0, named("io.dropwizard.views.View")))
            .and(isPublic()),
        DropwizardViewInstrumentation.class.getName() + "$RenderAdvice");
  }

  public static class RenderAdvice {
    public static final Tracer TRACER =
        OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.dropwizard-views-0.7");

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.This final Object obj, @Advice.Argument(0) final View view) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        return null;
      }
      final Span span = TRACER.spanBuilder("view.render").startSpan();
      span.setAttribute(MoreTags.RESOURCE_NAME, "View " + view.getTemplateName());
      span.setAttribute(Tags.COMPONENT, "dropwizard-view");
      span.setAttribute("span.origin.type", obj.getClass().getSimpleName());
      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        span.setStatus(Status.UNKNOWN);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
      spanWithScope.closeScope();
    }
  }
}
