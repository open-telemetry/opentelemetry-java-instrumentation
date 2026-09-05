/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

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
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0.VertxSqlClientSingletons.attachClientInfoProvider;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
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
import java.util.List;
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
    // In vertx 4.x, database-specific sub-interfaces like PgPool and MySQLPool declare their own
    // static pool() methods that take subtypes of SqlConnectOptions (e.g. PgConnectOptions) and
    // return subtypes of Pool. These are independent static methods, not overrides, and have their
    // own separate code paths. hasSuperType is needed to match these variant signatures.
    transformer.applyAdviceToMethod(
        named("pool")
            .and(isStatic())
            .and(takesArguments(3))
            .and(takesArgument(1, hasSuperType(named("io.vertx.sqlclient.SqlConnectOptions"))))
            .and(returns(hasSuperType(named("io.vertx.sqlclient.Pool")))),
        getClass().getName() + "$PoolAdvice");

    // Added in 4.2, these overloads take the servers the client load balances over.
    transformer.applyAdviceToMethod(
        namedOneOf("client", "pool")
            .and(isStatic())
            .and(takesArguments(3))
            .and(takesArgument(1, named("java.util.List")))
            .and(returns(hasSuperType(named("io.vertx.sqlclient.SqlClient")))),
        getClass().getName() + "$ServerListAdvice");

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
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      String dbSystemName = resolveDbSystemName(sqlConnectOptions, declaringTypeName);
      VertxSqlClientInfoCapture infoCapture = new VertxSqlClientInfoCapture();
      infoCapture.setDbSystemName(dbSystemName);
      infoCapture.setInfo(VertxSqlClientInfo.create(sqlConnectOptions, dbSystemName));
      setClientInfoProvider(infoCapture);
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return Pool pool,
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
    }
  }

  @SuppressWarnings("unused")
  public static class ServerListAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static CallDepth onEnter(
        @Advice.Argument(1) List<SqlConnectOptions> databases,
        @Advice.Origin("#t") String declaringTypeName) {
      CallDepth callDepth = CallDepth.forClass(Pool.class);
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      SqlConnectOptions first = databases == null || databases.isEmpty() ? null : databases.get(0);
      String dbSystemName = resolveDbSystemName(first, declaringTypeName);
      VertxSqlClientInfoCapture infoCapture = new VertxSqlClientInfoCapture();
      infoCapture.setDbSystemName(dbSystemName);
      infoCapture.setInfo(VertxSqlClientInfo.create(databases, dbSystemName));
      setClientInfoProvider(infoCapture);
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Return Object client,
        @Advice.Argument(1) List<SqlConnectOptions> databases,
        @Advice.Enter CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      VertxSqlClientInfoProvider infoProvider = getClientInfoProvider();
      if (client != null && infoProvider instanceof VertxSqlClientInfoCapture) {
        VertxSqlClientInfoCapture infoCapture = (VertxSqlClientInfoCapture) infoProvider;
        String dbSystemName = infoCapture.getDbSystemName();
        if (dbSystemName == null || !isKnownDbSystem(dbSystemName)) {
          dbSystemName = getDbSystemNameFromClassName(client);
          infoCapture.setDbSystemName(dbSystemName);
        }
        infoCapture.setInfo(VertxSqlClientInfo.create(databases, dbSystemName));
      }
      if (client instanceof Pool) {
        setPoolClientInfoProvider((Pool) client, infoProvider);
      }
      setClientInfoProvider(null);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static Future<SqlConnection> onExit(
        @Advice.This Pool pool, @Advice.Return Future<SqlConnection> future) {
      return wrapContext(attachClientInfoProvider(future, getPoolClientInfoProvider(pool)));
    }
  }
}
