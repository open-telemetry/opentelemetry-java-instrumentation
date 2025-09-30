/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.testing;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AgentSpanTestingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.javaagent.instrumentation.testing.AgentSpanTesting");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("runWithHttpServerSpan"), this.getClass().getName() + "$RunWithHttpServerSpanAdvice");
    transformer.applyAdviceToMethod(
        named("runWithAllSpanKeys"), this.getClass().getName() + "$RunWithAllSpanKeysAdvice");
  }

  public static class AdviceScope {
    private final Context context;
    private final Scope scope;

    public AdviceScope(Context context, Scope scope) {
      this.context = context;
      this.scope = scope;
    }

    public Context getContext() {
      return context;
    }

    public void end() {
      scope.close();
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithHttpServerSpanAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) String spanName) {
      Context context = AgentSpanTestingInstrumenter.startHttpServerSpan(spanName);
      return new AdviceScope(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end();
      AgentSpanTestingInstrumenter.endHttpServer(adviceScope.getContext(), throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithAllSpanKeysAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) String spanName) {
      Context context = AgentSpanTestingInstrumenter.startSpanWithAllKeys(spanName);
      return new AdviceScope(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end();
      AgentSpanTestingInstrumenter.end(adviceScope.getContext(), throwable);
    }
  }
}
