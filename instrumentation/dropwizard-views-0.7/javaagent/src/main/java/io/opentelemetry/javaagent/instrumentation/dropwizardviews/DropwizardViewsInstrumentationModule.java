/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import static io.opentelemetry.javaagent.instrumentation.dropwizardviews.DropwizardTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.dropwizard.views.View;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class DropwizardViewsInstrumentationModule extends InstrumentationModule {
  public DropwizardViewsInstrumentationModule() {
    super("dropwizard-views");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ViewRendererInstrumentation());
  }

  public static class ViewRendererInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("io.dropwizard.views.ViewRenderer");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("io.dropwizard.views.ViewRenderer"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(named("render"))
              .and(takesArgument(0, named("io.dropwizard.views.View")))
              .and(isPublic()),
          DropwizardViewsInstrumentationModule.class.getName() + "$RenderAdvice");
    }
  }

  public static class RenderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) View view,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (Java8BytecodeBridge.currentSpan().getSpanContext().isValid()) {
        context = tracer().startSpan("Render " + view.getTemplateName());
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      if (throwable == null) {
        tracer().end(context);
      } else {
        tracer().endExceptionally(context, throwable);
      }
    }
  }
}
