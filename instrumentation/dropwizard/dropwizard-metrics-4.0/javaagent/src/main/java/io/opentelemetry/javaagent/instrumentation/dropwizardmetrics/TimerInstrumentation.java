/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import static io.opentelemetry.javaagent.instrumentation.dropwizardmetrics.DropwizardSingletons.metrics;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.codahale.metrics.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class TimerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.codahale.metrics.Timer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("update").and(takesArguments(long.class)),
        this.getClass().getName() + "$UpdateAdvice");
  }

  @SuppressWarnings({"PrivateConstructorForUtilityClass", "unused"})
  public static class UpdateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Timer timer, @Advice.Argument(0) long nanos) {
      metrics().timerUpdate(timer, nanos);
    }
  }
}
