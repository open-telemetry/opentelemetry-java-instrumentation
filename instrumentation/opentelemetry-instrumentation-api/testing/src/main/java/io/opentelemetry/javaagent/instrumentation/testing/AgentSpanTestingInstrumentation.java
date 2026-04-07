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

class AgentSpanTestingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.javaagent.instrumentation.testing.AgentSpanTesting");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("runWithHttpServerSpan"), getClass().getName() + "$RunWithHttpServerSpanAdvice");
    transformer.applyAdviceToMethod(
        named("runWithAllSpanKeys"), getClass().getName() + "$RunWithAllSpanKeysAdvice");
  }

  private static final class AdviceScope {
    private final Context context;
    private final Scope scope;

    private AdviceScope(Context context, Scope scope) {
      this.context = context;
      this.scope = scope;
    }

    private Context getContext() {
      return context;
    }

    private void end() {
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
