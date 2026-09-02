/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.dbcp.v8_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.dbcp.v8_0.TomcatDbcpSingletons.getDataSourceName;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.management.ObjectName;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.apache.tomcat.dbcp.dbcp2.OpenTelemetryBasicDataSourceUtil;

class BasicDataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.tomcat.dbcp.dbcp2.BasicDataSource");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("startPoolMaintenance").and(takesArguments(0)),
        getClass().getName() + "$StartPoolMaintenanceAdvice");

    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("close")).and(takesArguments(0)),
        getClass().getName() + "$CloseAdvice");

    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("preRegister")).and(takesArguments(2)),
        getClass().getName() + "$PreRegisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartPoolMaintenanceAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This BasicDataSource dataSource) {
      ObjectName objectName = OpenTelemetryBasicDataSourceUtil.getRegisteredJmxName(dataSource);
      String dataSourceName =
          objectName != null ? getDataSourceName(objectName) : getDataSourceName(dataSource);
      TomcatDbcpDataSourceMetrics.registerMetrics(dataSource, dataSourceName);
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.This BasicDataSource dataSource) {
      TomcatDbcpDataSourceMetrics.unregisterMetrics(dataSource);
    }
  }

  @SuppressWarnings("unused")
  public static class PreRegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This BasicDataSource dataSource, @Advice.Return ObjectName objectName) {
      if (objectName == null) {
        return;
      }

      String dataSourceName = getDataSourceName(objectName);

      TomcatDbcpDataSourceMetrics.unregisterMetrics(dataSource);
      TomcatDbcpDataSourceMetrics.registerMetrics(dataSource, dataSourceName);
    }
  }
}
