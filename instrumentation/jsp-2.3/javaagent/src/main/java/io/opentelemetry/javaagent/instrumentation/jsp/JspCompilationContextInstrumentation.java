/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.javaagent.instrumentation.jsp.JspCompilationContextInstrumentationSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.jasper.JspCompilationContext;

public class JspCompilationContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.jasper.JspCompilationContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("compile").and(takesArguments(0)).and(isPublic()),
        JspCompilationContextInstrumentation.class.getName() + "$CompileAdvice");
  }

  @SuppressWarnings("unused")
  public static class CompileAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      private AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(JspCompilationContext jspCompilationContext) {
        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, jspCompilationContext)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, jspCompilationContext);
        return new AdviceScope(context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable, JspCompilationContext jspCompilationContext) {
        scope.close();
        instrumenter().end(context, jspCompilationContext, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.This JspCompilationContext jspCompilationContext) {
      return AdviceScope.start(jspCompilationContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This JspCompilationContext jspCompilationContext,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, jspCompilationContext);
      }
    }
  }
}
