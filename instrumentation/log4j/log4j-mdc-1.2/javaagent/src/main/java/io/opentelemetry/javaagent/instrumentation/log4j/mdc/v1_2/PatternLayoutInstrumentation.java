/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.internal.LogPatternInjection;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PatternLayoutInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.PatternLayout");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().or(named("setConversionPattern")).and(takesArguments(String.class)),
        getClass().getName() + "$PatternAdvice");
  }

  @SuppressWarnings("unused")
  public static class PatternAdvice {
    @AssignReturned.ToArguments(@AssignReturned.ToArguments.ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static String onEnter(@Advice.Argument(0) String pattern) {
      if (!LogPatternInjection.isEnabled()) {
        return pattern;
      }
      return LogPatternInjection.inject(pattern, Log4j1PatternInjection.FRAMEWORK);
    }
  }
}
