/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.contextdata.v2_17;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PatternLayoutInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.logging.log4j.core.layout.PatternLayout$Builder",
        "org.apache.logging.log4j.core.layout.PatternLayout");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(isDeclaredBy(named("org.apache.logging.log4j.core.layout.PatternLayout"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("build").and(takesArguments(0)), getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @AssignReturned.ToFields(@AssignReturned.ToFields.ToField("conversionPattern"))
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Nullable
    public static String onExit(@Advice.FieldValue("conversionPattern") @Nullable String pattern) {
      return LogPatternInjection.restoreOriginalPattern(pattern);
    }
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {
    @AssignReturned.ToFields(@AssignReturned.ToFields.ToField(value = "pattern", index = 2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.FieldValue("pattern") @Nullable String pattern,
        @Advice.FieldValue("patternSelector") Object selector) {
      if (!LogPatternInjection.isEnabled()) {
        return null;
      }
      LogPatternInjection.Injection injection =
          LogPatternInjection.prepareLog4j2(selector == null ? pattern : null);
      String enhanced = selector == null ? injection.getPattern() : pattern;
      Object[] state = new Object[] {null, pattern, enhanced};
      state[0] = LogPatternInjection.currentInjection().set(injection);
      return state;
    }

    @AssignReturned.ToFields(@AssignReturned.ToFields.ToField("pattern"))
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Nullable
    public static String onExit(
        @Advice.Enter @Nullable Object[] state,
        @Advice.FieldValue("pattern") @Nullable String pattern) {
      if (state == null) {
        return pattern;
      }
      LogPatternInjection.currentInjection().restore((LogPatternInjection.Injection) state[0]);
      return (String) state[1];
    }
  }
}
