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
      LogPatternInjection.Injection injection = LogPatternInjection.prepare(pattern, framework);
      String enhanced = injection.getPattern();
      Object[] state =
          new Object[] {
            null, pattern, enhanced, enhanced == null ? length : enhanced.length(), length
          };
      state[0] = LogPatternInjection.currentInjection().set(injection);
      return state;
    }

    @AssignReturned.ToFields({
      @AssignReturned.ToFields.ToField(value = "pattern", index = 0),
      @AssignReturned.ToFields.ToField(value = "patternLength", index = 1)
    })
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Nullable
    public static Object[] onExit(@Advice.Enter @Nullable Object[] state) {
      if (state == null) {
        return null;
      }
      LogPatternInjection.currentInjection().restore((LogPatternInjection.Injection) state[0]);
      return new Object[] {state[1], state[4]};
    }
  }
}
