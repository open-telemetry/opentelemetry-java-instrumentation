/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.c3p0.v0_9;

import static io.opentelemetry.javaagent.instrumentation.c3p0.v0_9.C3p0Singletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionPoolMetricsInfo;
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
        named("resetPoolManager"), getClass().getName() + "$ResetPoolManagerAdvice");
    transformer.applyAdviceToMethod(named("close"), getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResetPoolManagerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This AbstractPoolBackedDataSource dataSource) {
      JdbcConnectionPoolMetricsInfo metricsInfo = C3p0Singletons.getMetricsInfo(dataSource);
      String dataSourceName = dataSource.getDataSourceName();
      if (dataSourceName != null && !dataSourceName.equals(dataSource.getIdentityToken())) {
        metricsInfo = metricsInfo.withPoolName(dataSourceName);
      }
      telemetry()
          .registerMetrics(
              dataSource, metricsInfo.getPoolName(), metricsInfo.getDatabaseAttributes());
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.This AbstractPoolBackedDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
