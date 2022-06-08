/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oracleucp;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.oracleucp.OracleUcpSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import oracle.ucp.UniversalConnectionPool;

public class UniversalConnectionPoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("oracle.ucp.UniversalConnectionPool");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("oracle.ucp.UniversalConnectionPool"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("start")
            .and(takesArguments(0).or(takesArguments(1).and(takesArgument(0, boolean.class)))),
        this.getClass().getName() + "$StartAdvice");
    transformer.applyAdviceToMethod(
        named("stop").and(takesArguments(0)), this.getClass().getName() + "$StopAdvice");
  }

  @SuppressWarnings("unused")
  public static class StartAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This UniversalConnectionPool connectionPool) {
      telemetry().registerMetrics(connectionPool);
    }
  }

  @SuppressWarnings("unused")
  public static class StopAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This UniversalConnectionPool connectionPool) {
      telemetry().unregisterMetrics(connectionPool);
    }
  }
}
