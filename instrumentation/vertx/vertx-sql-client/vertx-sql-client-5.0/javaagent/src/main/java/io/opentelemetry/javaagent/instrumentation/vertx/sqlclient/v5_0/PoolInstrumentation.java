/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getDbSystemNameFromClassName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getPoolClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.isKnownDbSystem;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.resolveDbSystemName;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolClientInfoProvider;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.wrapContext;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoCapture;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfoProvider;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class PoolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.vertx.sqlclient.Pool");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.vertx.sqlclient.Pool"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pool")
            .and(isStatic())
            .and(takesArguments(3))
            .and(takesArgument(1, named("io.vertx.sqlclient.SqlConnectOptions")))
            .and(returns(named("io.vertx.sqlclient.Pool"))),
        getClass().getName() + "$PoolAdvice");

    transformer.applyAdviceToMethod(
        named("getConnection").and(takesNoArguments()).and(returns(named("io.vertx.core.Future"))),
        getClass().getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class PoolAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallDepth onEnter(
        @Advice.Argument(1) SqlConnectOptions sqlConnectOptions,
        @Advice.Origin("#t") String declaringTypeName) {
      CallDepth callDepth = CallDepth.forClass(Pool.class);
      if (callDepth.getAndIncrement() == 0) {
        String dbSystemName = resolveDbSystemName(sqlConnectOptions, declaringTypeName);
        VertxSqlClientInfoCapture infoCapture = new VertxSqlClientInfoCapture();
        infoCapture.setDbSystemName(dbSystemName);
        infoCapture.setInfo(VertxSqlClientInfo.create(sqlConnectOptions, dbSystemName));
        setClientInfoProvider(infoCapture);
        VertxSqlClientSingletons.setBuildingSupplierCapture(infoCapture);
      }
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return @Nullable Pool pool,
        @Advice.Argument(1) SqlConnectOptions sqlConnectOptions,
        @Advice.Enter CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      VertxSqlClientInfoProvider infoProvider = getClientInfoProvider();
      if (pool != null && infoProvider instanceof VertxSqlClientInfoCapture) {
        VertxSqlClientInfoCapture infoCapture = (VertxSqlClientInfoCapture) infoProvider;
        String dbSystemName = infoCapture.getDbSystemName();
        if (dbSystemName == null || !isKnownDbSystem(dbSystemName)) {
          dbSystemName = getDbSystemNameFromClassName(pool);
          infoCapture.setDbSystemName(dbSystemName);
        }
        infoCapture.setInfo(VertxSqlClientInfo.create(sqlConnectOptions, dbSystemName));
      }
      if (pool != null) {
        setPoolClientInfoProvider(pool, infoProvider);
      }
      setClientInfoProvider(null);
      VertxSqlClientSingletons.setBuildingSupplierCapture(null);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(@Advice.This Pool pool) {
      VertxSqlClientInfoCapture supplierCapture =
          VertxSqlClientSingletons.getPoolSupplierCapture(pool);
      if (supplierCapture == null) {
        return null;
      }
      Object connectionRequest = new Object();
      supplierCapture.addConnectionRequest(connectionRequest);
      return connectionRequest;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Nullable
    public static Future<SqlConnection> onExit(
        @Advice.This Pool pool,
        @Advice.Return @Nullable Future<SqlConnection> future,
        @Advice.Enter @Nullable Object connectionRequest) {
      VertxSqlClientInfoProvider infoProvider = getPoolClientInfoProvider(pool);
      VertxSqlClientInfoCapture supplierCapture =
          infoProvider instanceof VertxSqlClientInfoCapture
              ? (VertxSqlClientInfoCapture) infoProvider
              : null;
      if (future == null) {
        if (supplierCapture != null && connectionRequest != null) {
          supplierCapture.removeConnectionRequest(connectionRequest);
        }
        return null;
      }
      return wrapContext(
          VertxSqlClientSingletons.attachClientInfoProvider(
              future, infoProvider, connectionRequest));
    }
  }
}
