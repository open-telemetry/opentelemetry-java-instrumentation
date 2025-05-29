/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql;

import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.getSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.setSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.attachConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.v4_0.sql.VertxSqlClientSingletons.getSqlConnectOptions;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.impl.SqlClientBase;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SqlClientBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.SqlClientBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), SqlClientBaseInstrumentation.class.getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("query", "preparedQuery"),
        SqlClientBaseInstrumentation.class.getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This SqlClientBase<?> sqlClientBase) {
      // copy connection options from ThreadLocal to VirtualField
      attachConnectOptions(sqlClientBase, getSqlConnectOptions());
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This SqlClientBase<?> sqlClientBase,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      // set connection options to ThreadLocal, they will be read in QueryExecutor constructor
      SqlConnectOptions sqlConnectOptions = getSqlConnectOptions(sqlClientBase);
      setSqlConnectOptions(sqlConnectOptions);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      setSqlConnectOptions(null);
    }
  }
}
