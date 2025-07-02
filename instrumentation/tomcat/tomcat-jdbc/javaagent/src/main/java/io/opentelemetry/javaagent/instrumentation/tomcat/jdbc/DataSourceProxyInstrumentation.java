/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

class DataSourceProxyInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.tomcat.jdbc.pool.DataSourceProxy");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("createPool")).and(takesNoArguments()),
        this.getClass().getName() + "$CreatePoolAdvice");

    transformer.applyAdviceToMethod(
        isPublic().and(named("close")).and(takesArguments(1)),
        this.getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreatePoolAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This DataSourceProxy dataSource) {
      TomcatConnectionPoolMetrics.registerMetrics(dataSource);
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This DataSourceProxy dataSource) {
      TomcatConnectionPoolMetrics.unregisterMetrics(dataSource);
    }
  }
}
