/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import static io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0.ApacheDbcpSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.management.ObjectName;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.dbcp2.BasicDataSource;

class BasicDataSourceInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.commons.dbcp2.BasicDataSource");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("preRegister")).and(takesArguments(2)),
        this.getClass().getName() + "$PreRegisterAdvice");

    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("postDeregister")),
        this.getClass().getName() + "$PostDeregisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class PreRegisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This BasicDataSource dataSource, @Advice.Return ObjectName objectName) {
      String dataSourceName;
      if (objectName != null) {
        dataSourceName = objectName.getKeyProperty("name");
        if (dataSourceName == null) {
          dataSourceName = objectName.toString();
        }
      } else {
        // fallback just in case it is somehow registered without a name
        dataSourceName = "dbcp2-" + System.identityHashCode(dataSource);
      }
      telemetry().registerMetrics(dataSource, dataSourceName);
    }
  }

  @SuppressWarnings("unused")
  public static class PostDeregisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This BasicDataSource dataSource) {
      telemetry().unregisterMetrics(dataSource);
    }
  }
}
