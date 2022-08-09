/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import static io.opentelemetry.javaagent.instrumentation.dropwizardmetrics.DropwizardSingletons.metrics;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.codahale.metrics.Counter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CounterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.codahale.metrics.Counter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("inc").and(takesArguments(long.class)), this.getClass().getName() + "$IncAdvice");
    transformer.applyAdviceToMethod(
        named("dec").and(takesArguments(long.class)), this.getClass().getName() + "$DecAdvice");
  }

  @SuppressWarnings("unused")
  public static class IncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Counter counter, @Advice.Argument(0) long increment) {
      metrics().counterAdd(counter, increment);
    }
  }

  @SuppressWarnings("unused")
  public static class DecAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Counter counter, @Advice.Argument(0) long decrement) {
      metrics().counterAdd(counter, -decrement);
    }
  }
}
