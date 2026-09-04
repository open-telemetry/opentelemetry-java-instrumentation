/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.attachPreparedStatementInfo;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoProvider;
import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.internal.SqlClientBase;
import io.vertx.sqlclient.internal.SqlConnectionBase;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SqlConnectionBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.sqlclient.internal.SqlConnectionBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("prepare").and(returns(named("io.vertx.core.Future"))),
        getClass().getName() + "$PrepareAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This SqlClientBase sqlClientBase, @Advice.Argument(2) Object connection) {
      VertxSqlClientInfo info = VertxSqlClientSingletons.getConnectionInfo(connection);
      if (info != null) {
        VertxSqlClientSingletons.attachClientInfoProvider(sqlClientBase, info);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PrepareAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallDepth onEnter() {
      CallDepth callDepth = CallDepth.forClass(SqlConnectionBase.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static Future<PreparedStatement> onExit(
        @Advice.This SqlClientBase sqlClientBase,
        @Advice.Return Future<PreparedStatement> future,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter CallDepth callDepth) {
      // prepare(String) delegates to prepare(String, PrepareOptions), only the outermost call
      // should attach the prepared statement data
      if (callDepth.decrementAndGet() > 0 || throwable != null) {
        return future;
      }

      VertxSqlClientInfoProvider infoProvider =
          VertxSqlClientSingletons.getClientInfoProvider(sqlClientBase);
      VertxSqlClientInfo info = infoProvider != null ? infoProvider.getInfo() : null;
      return info == null ? future : wrapContext(attachPreparedStatementInfo(future, info));
    }
  }
}
