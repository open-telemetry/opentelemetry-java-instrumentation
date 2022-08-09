/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0;

import static io.opentelemetry.javaagent.instrumentation.c3p0.C3p0Singletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class AbstractPoolBackedDataSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("resetPoolManager"), this.getClass().getName() + "$ResetPoolManagerAdvice");
    transformer.applyAdviceToMethod(named("close"), this.getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResetPoolManagerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This AbstractPoolBackedDataSource dataSource) {
      telemetry().registerMetrics(dataSource);
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This AbstractPoolBackedDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
