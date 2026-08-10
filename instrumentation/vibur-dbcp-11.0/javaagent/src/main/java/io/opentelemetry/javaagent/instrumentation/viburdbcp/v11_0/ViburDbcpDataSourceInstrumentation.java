/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.viburdbcp.v11_0;

import static io.opentelemetry.javaagent.instrumentation.viburdbcp.v11_0.ViburSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Properties;
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
        named("configureFromProperties").and(takesArguments(Properties.class)),
        getClass().getName() + "$ConfigureFromPropertiesAdvice");
    transformer.applyAdviceToMethod(
        named("start").and(takesArguments(0)), getClass().getName() + "$StartAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(takesArguments(0)), getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConfigureFromPropertiesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This ViburDBCPDataSource dataSource, @Advice.Argument(0) Properties properties) {
      if (properties.containsKey("name")) {
        ViburSingletons.markDataSourceNameConfigured(dataSource, properties.getProperty("name"));
      }
    }
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This ViburDBCPDataSource dataSource) {
      String poolName = dataSource.getName();
      if (!ViburSingletons.isDataSourceNameConfigured(dataSource)) {
        poolName = ViburSingletons.getDataSourceName(dataSource);
      }
      telemetry().registerMetrics(dataSource, poolName);
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.This ViburDBCPDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
