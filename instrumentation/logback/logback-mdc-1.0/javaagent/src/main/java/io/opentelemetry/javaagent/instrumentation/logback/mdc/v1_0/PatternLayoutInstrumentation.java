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
    public static String onEnter(@Advice.This PatternLayoutBase<?> layout) {
      if (!(layout instanceof PatternLayout) || !LogPatternInjection.isEnabled()) {
        return null;
      }
      String original = layout.getPattern();
      String enhanced = LogPatternInjection.enhance(original, LogbackPatternInjection.FRAMEWORK);
      if (original == null || enhanced == null || original.equals(enhanced)) {
        return null;
      }
      layout.setPattern(enhanced);
      return original;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This PatternLayoutBase<?> layout, @Advice.Enter @Nullable String original) {
      if (original != null) {
        layout.setPattern(original);
      }
    }
  }
}
