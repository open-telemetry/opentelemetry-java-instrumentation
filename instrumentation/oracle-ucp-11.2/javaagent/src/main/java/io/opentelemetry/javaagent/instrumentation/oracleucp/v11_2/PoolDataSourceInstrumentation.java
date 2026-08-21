/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp.v11_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

final class PoolDataSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("oracle.ucp.jdbc.PoolDataSourceImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createUniversalConnectionPool").and(takesArguments(0)),
        getClass().getName() + "$CreatePoolAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreatePoolAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter(@Advice.This PoolDataSource dataSource) {
      String poolName = dataSource.getConnectionPoolName();
      return poolName == null || poolName.isEmpty();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This PoolDataSource dataSource,
        @Advice.Enter boolean generatedPoolName,
        @Advice.Return @Nullable UniversalConnectionPool connectionPool) {
      if (generatedPoolName && connectionPool != null) {
        OracleUcpSingletons.capturePoolName(dataSource, connectionPool);
      }
    }
  }
}
