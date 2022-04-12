/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HooksInstrumentation implements TypeInstrumentation {
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
        isTypeInitializer().or(named("resetOnEachOperator")),
        this.getClass().getName() + "$ResetOnEachOperatorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResetOnEachOperatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void postStaticInitializer() {
      ContextPropagationOperator.builder()
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBoolean("otel.instrumentation.reactor.experimental-span-attributes", false))
          .build()
          .registerOnEachOperator();
    }
  }
}
