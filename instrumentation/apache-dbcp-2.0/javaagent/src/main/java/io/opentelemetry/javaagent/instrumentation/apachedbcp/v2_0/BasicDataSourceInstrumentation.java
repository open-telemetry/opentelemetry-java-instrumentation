/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import static io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0.ApacheDbcpSingletons.getDefaultName;
import static io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0.ApacheDbcpSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.management.ObjectName;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.OpenTelemetryBasicDataSourceUtil;

class BasicDataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.commons.dbcp2.BasicDataSource");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        named("startPoolMaintenance").and(takesArguments(0)),
        getClass().getName() + "$StartPoolMaintenanceAdvice");

    typeTransformer.applyAdviceToMethod(
        isPublic().and(namedOneOf("close", "postDeregister")).and(takesArguments(0)),
        getClass().getName() + "$DeregisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartPoolMaintenanceAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This BasicDataSource dataSource) {
      String dataSourceName = dataSource.getJmxName();
      if (dataSourceName == null) {
        ObjectName objectName = OpenTelemetryBasicDataSourceUtil.getRegisteredJmxName(dataSource);
        if (objectName != null) {
          dataSourceName = objectName.getKeyProperty("name");
          if (dataSourceName == null) {
            dataSourceName = objectName.toString();
          }
        }
      }
      if (dataSourceName == null) {
        dataSourceName = getDefaultName();
      }
      telemetry().registerMetrics(dataSource, dataSourceName);
    }
  }

  @SuppressWarnings("unused")
  public static class DeregisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This BasicDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
