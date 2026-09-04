/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setClientDataProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setSqlConnectOptions;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataProvider;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.internal.SqlClientBase;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlClientBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.internal.SqlClientBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("query", "preparedQuery"), getClass().getName() + "$QueryAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This SqlClientBase sqlClientBase) {
      VertxSqlClientSingletons.attachClientState(
          sqlClientBase,
          getSqlConnectOptions(),
          getAddressGroup(),
          VertxSqlClientSingletons.getBuildingDataCapture());
    }
  }

  @SuppressWarnings("unused")
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallDepth onEnter(@Advice.This SqlClientBase sqlClientBase) {
      CallDepth callDepth = CallDepth.forClass(SqlClientBase.class);
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      VertxSqlClientDataProvider dataProvider =
          VertxSqlClientSingletons.getDataProvider(sqlClientBase);
      if (dataProvider != null) {
        setClientDataProvider(dataProvider);
      } else {
        SqlConnectOptions sqlConnectOptions =
            VertxSqlClientSingletons.getSqlConnectOptions(sqlClientBase);
        setSqlConnectOptions(sqlConnectOptions);
        setAddressGroup(VertxSqlClientSingletons.getAddressGroup(sqlClientBase));
      }
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      setSqlConnectOptions(null);
      setAddressGroup(null);
      setClientDataProvider(null);
    }
  }
}
