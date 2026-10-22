/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
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
        getClass().getName() + "$ResetOnEachOperatorAdvice");
    transformer.applyAdviceToMethod(
        named("resetOnEachOperator").and(takesArguments(0)),
        getClass().getName() + "$ResetAllOnEachOperatorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResetOnEachOperatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void postStaticInitializer() {
      ContextPropagationOperator.builder()
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "reactor")
                  .getBoolean("experimental_span_attributes/development", false))
          .build()
          .registerOnEachOperator();
    }
  }

  @SuppressWarnings("unused")
  public static class ResetAllOnEachOperatorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void postResetAll() {
      // Hooks.resetOnEachOperator() has removed our hook without telling the operator, which
      // still considers it registered and would ignore a plain registration.
      ContextPropagationOperator operator =
          ContextPropagationOperator.builder()
              .setCaptureExperimentalSpanAttributes(
                  DeclarativeConfigUtil.getInstrumentationConfig(
                          GlobalOpenTelemetry.get(), "reactor")
                      .getBoolean("experimental_span_attributes/development", false))
              .build();
      operator.resetOnEachOperator();
      operator.registerOnEachOperator();
    }
  }
}
