/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getPoolAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.getPoolSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolAddressGroup;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setPoolConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientUtil.setSqlConnectOptions;
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
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlAddressGroup;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientDataCapture;
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
    // Match both the Pool interface (for static pool() factory methods) and classes/interfaces
    // that implement/extend Pool (for instance methods like getConnection())
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
    public static CallDepth onEnter(@Advice.Argument(1) SqlConnectOptions sqlConnectOptions) {
      CallDepth callDepth = CallDepth.forClass(Pool.class);
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      // set connection options to ThreadLocal, they will be read in SqlClientBase constructor
      setSqlConnectOptions(sqlConnectOptions);
      setAddressGroup(VertxSqlAddressGroup.of(sqlConnectOptions));
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

      if (pool != null) {
        setPoolConnectOptions(pool, sqlConnectOptions);
        setPoolAddressGroup(pool, VertxSqlAddressGroup.of(sqlConnectOptions));
        VertxSqlClientSingletons.resolveAndStoreDbSystem(pool, sqlConnectOptions);
      }
      setSqlConnectOptions(null);
      setAddressGroup(null);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(@Advice.This Pool pool) {
      VertxSqlClientDataCapture dataCapture = VertxSqlClientSingletons.getPoolDataCapture(pool);
      if (dataCapture == null) {
        return null;
      }
      Object connectionRequest = new Object();
      dataCapture.addConnectionRequest(connectionRequest);
      return connectionRequest;
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    @Nullable
    public static Future<SqlConnection> onExit(
        @Advice.This Pool pool,
        @Advice.Return @Nullable Future<SqlConnection> future,
        @Advice.Enter @Nullable Object connectionRequest) {
      VertxSqlClientDataCapture dataCapture = VertxSqlClientSingletons.getPoolDataCapture(pool);
      if (future == null) {
        if (dataCapture != null && connectionRequest != null) {
          dataCapture.removeConnectionRequest(connectionRequest);
        }
        return null;
      }
      return wrapContext(
          VertxSqlClientSingletons.attachClientState(
              future,
              getPoolSqlConnectOptions(pool),
              getPoolAddressGroup(pool),
              dataCapture,
              connectionRequest));
    }
  }
}
