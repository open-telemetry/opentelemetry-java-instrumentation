/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class HooksInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "reactor.core.publisher.Hooks",
        // Hooks may not be loaded early enough so also match our main targets
        "reactor.core.publisher.Flux",
        "reactor.core.publisher.Mono");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isTypeInitializer().or(named("resetOnEachOperator").and(takesArguments(1))),
        getClass().getName() + "$RegisterOnEachOperatorAdvice");
    // The operator itself resets its hook by key, so only the reset of all hooks re-registers.
    transformer.applyAdviceToMethod(
        named("resetOnEachOperator").and(takesArguments(0)),
        getClass().getName() + "$ResetOnEachOperatorAdvice");
  }

  @SuppressWarnings("unused")
  public static class RegisterOnEachOperatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void postStaticInitializer() {
      ReactorSingletons.registerOnEachOperator();
    }
  }

  @SuppressWarnings("unused")
  public static class ResetOnEachOperatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void postReset() {
      ReactorSingletons.reregisterOnEachOperator();
    }
  }
}
