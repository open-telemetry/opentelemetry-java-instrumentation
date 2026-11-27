/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PatternParserInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.helpers.PatternParser");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("parse").and(takesArguments(0)), getClass().getName() + "$PatternAdvice");
  }

  @SuppressWarnings("unused")
  public static class PatternAdvice {
    @AssignReturned.ToFields({
      @AssignReturned.ToFields.ToField(value = "pattern", index = 2),
      @AssignReturned.ToFields.ToField(value = "patternLength", index = 3)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onEnter(
        @Advice.FieldValue("pattern") String pattern,
        @Advice.FieldValue("patternLength") int length) {
      if (!LogPatternInjection.isEnabled()) {
        return null;
      }
      // The compatibility probe itself invokes PatternParser.parse() during class initialization.
      LogPatternInjection.Framework framework = Log4j1PatternInjection.FRAMEWORK;
      if (framework == null) {
        return null;
      }
      String enhanced = LogPatternInjection.enhance(pattern, framework);
      return enhanced == null || pattern.equals(enhanced)
          ? null
          : new Object[] {pattern, length, enhanced, enhanced.length()};
    }

    @AssignReturned.ToFields({
      @AssignReturned.ToFields.ToField(value = "pattern", index = 0),
      @AssignReturned.ToFields.ToField(value = "patternLength", index = 1)
    })
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onExit(@Advice.Enter @Nullable Object[] state) {
      return state;
    }
  }
}
