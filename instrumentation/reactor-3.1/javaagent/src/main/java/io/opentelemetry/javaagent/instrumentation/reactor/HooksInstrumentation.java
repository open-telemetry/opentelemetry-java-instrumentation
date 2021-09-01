/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.reactor.TracingOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HooksInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.core.publisher.Hooks");
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
      Config config = Config.get();
      TracingOperator.newBuilder()
          .setCaptureExperimentalSpanAttributes(
              config.getBoolean("otel.instrumentation.reactor.experimental-span-attributes", false))
          .setEmitCheckpoints(
              config.getBoolean("otel.instrumentation.reactor.emit-checkpoints", false))
          .setTraceMultipleSubscribers(
              config.getBoolean("otel.instrumentation.reactor.trace-multiple-subscribers", false))
          .build()
          .registerOnEachOperator();
    }
  }
}
