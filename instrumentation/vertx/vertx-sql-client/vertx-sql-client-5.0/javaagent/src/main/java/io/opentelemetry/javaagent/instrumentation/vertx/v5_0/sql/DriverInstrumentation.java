/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.sql;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.getDbSystemNameFromClassName;
import static io.opentelemetry.javaagent.instrumentation.vertx.v5_0.sql.VertxSqlClientSingletons.storePoolDbSystem;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.sqlclient.Pool;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DriverInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.sqlclient.spi.Driver");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.sqlclient.spi.Driver"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("newPool")
            .and(not(isStatic()))
            .and(takesArguments(6))
            .and(returns(named("io.vertx.sqlclient.Pool"))),
        getClass().getName() + "$NewPoolAdvice");
  }

  @SuppressWarnings("unused")
  public static class NewPoolAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Object driver, @Advice.Return Pool pool) {
      if (pool != null) {
        storePoolDbSystem(pool, getDbSystemNameFromClassName(driver));
      }
    }
  }
}
