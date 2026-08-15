/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getDbSystemNameFromClassName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.impl.QueryExecutorUtil;
import io.vertx.sqlclient.impl.SqlClientBase;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlConnectionBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.impl.SqlConnectionBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("prepare").and(returns(named("io.vertx.core.Future"))),
        getClass().getName() + "$PrepareAdvice");
  }

  @SuppressWarnings("unused")
  public static class PrepareAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Future<PreparedStatement> onExit(
        @Advice.This SqlClientBase<?> sqlClientBase,
        @Advice.Return Future<PreparedStatement> future) {
      SqlConnectOptions connectOptions =
          VertxSqlClientSingletons.getSqlConnectOptions(sqlClientBase);
      String dbSystem = null;
      if (connectOptions != null) {
        dbSystem = VertxSqlClientSingletons.getConnectOptionsDbSystem(connectOptions);
        if (dbSystem == null) {
          dbSystem = getDbSystemNameFromClassName(connectOptions);
        }
      }
      return wrapContext(
          QueryExecutorUtil.attachPreparedStatementData(future, connectOptions, dbSystem));
    }
  }
}
