/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp;

import static io.opentelemetry.javaagent.instrumentation.viburdbcp.ViburSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.vibur.dbcp.ViburDBCPDataSource;

final class ViburDbcpDataSourceInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.vibur.dbcp.ViburDBCPDataSource");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start").and(takesArguments(0)), this.getClass().getName() + "$StartAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(takesArguments(0)), this.getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This ViburDBCPDataSource dataSource) {
      telemetry().registerMetrics(dataSource);
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This ViburDBCPDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
