/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp.v3_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HikariPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.zaxxer.hikari.pool.HikariPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // this method is always called in the HikariPool constructor, even if the user does not
    // configure anything
    transformer.applyAdviceToMethod(
        named("setMetricsTrackerFactory")
            .and(takesArguments(1))
            .and(takesArgument(0, named("com.zaxxer.hikari.metrics.MetricsTrackerFactory"))),
        this.getClass().getName() + "$SetMetricsTrackerFactoryAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetMetricsTrackerFactoryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) MetricsTrackerFactory userMetricsTracker,
        @Advice.FieldValue("metricsTracker") AutoCloseable existingMetricsTracker)
        throws Exception {

      if (existingMetricsTracker != null) {
        // we call close on the existing metrics tracker in case it's our wrapper, so that our
        // wrapper will unregister itself and won't keep recording metrics which leads to warnings
        // about duplicate metrics
        existingMetricsTracker.close();
      }
      userMetricsTracker = HikariSingletons.createMetricsTrackerFactory(userMetricsTracker);
    }
  }
}
