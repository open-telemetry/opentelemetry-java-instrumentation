/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PatternLayoutInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ch.qos.logback.core.pattern.PatternLayoutBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start").and(takesArguments(0)), getClass().getName() + "$StartAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(@Advice.This PatternLayoutBase<?> layout) {
      if (!(layout instanceof PatternLayout) || !LogPatternInjection.isEnabled()) {
        return null;
      }
      String original = layout.getPattern();
      LogPatternInjection.Injection injection =
          LogPatternInjection.prepare(original, LogbackPatternInjection.FRAMEWORK);
      layout.setPattern(injection.getPattern());
      Object[] state = new Object[] {null, original};
      state[0] = LogPatternInjection.currentInjection().set(injection);
      return state;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This PatternLayoutBase<?> layout, @Advice.Enter @Nullable Object[] state) {
      if (state != null) {
        LogPatternInjection.currentInjection().restore((LogPatternInjection.Injection) state[0]);
        layout.setPattern((String) state[1]);
      }
    }
  }
}
