/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.dropwizard.views.View;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("io.dropwizard.views.ViewRenderer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.dropwizard.views.ViewRenderer"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {getClass().getName() + "$RenderAdvice"};
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
    private static final Tracer TRACER =
        OpenTelemetry.getGlobalTracer("io.opentelemetry.auto.dropwizard-views-0.7");

    public static Tracer tracer() {
      return TRACER;
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(@Advice.Argument(0) View view) {
      if (!Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
        return null;
      }
      Span span = TRACER.spanBuilder("Render " + view.getTemplateName()).startSpan();
      return new SpanWithScope(span, span.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter SpanWithScope spanWithScope, @Advice.Thrown Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      Span span = spanWithScope.getSpan();
      if (throwable != null) {
        span.setStatus(StatusCode.ERROR);
        BaseDecorator.addThrowable(span, throwable);
      }
      span.end();
      spanWithScope.closeScope();
    }
  }
}
