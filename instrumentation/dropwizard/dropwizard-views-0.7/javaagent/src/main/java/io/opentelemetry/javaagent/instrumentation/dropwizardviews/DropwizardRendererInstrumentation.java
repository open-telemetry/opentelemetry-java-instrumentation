/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.dropwizardviews.DropwizardSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.dropwizard.views.View;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DropwizardRendererInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.dropwizard.views.ViewRenderer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.dropwizard.views.ViewRenderer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("render"))
            .and(takesArgument(0, named("io.dropwizard.views.View")))
            .and(isPublic()),
        this.getClass().getName() + "$RenderAdvice");
  }

  @SuppressWarnings("unused")
  public static class RenderAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(View view) {
        Context parentContext = Context.current();

        // don't start a new top-level span
        if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
          return null;
        }
        if (!instrumenter().shouldStart(parentContext, view)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, view);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(View view, @Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, view, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) View view) {
      return AdviceScope.start(view);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) View view,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(view, throwable);
      }
    }
  }
}
