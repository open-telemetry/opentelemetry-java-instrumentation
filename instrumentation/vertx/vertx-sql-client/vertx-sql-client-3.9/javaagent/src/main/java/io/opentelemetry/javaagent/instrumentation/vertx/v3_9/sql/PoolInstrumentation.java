/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.getPoolSqlConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.setPoolConnectOptions;
import static io.opentelemetry.javaagent.instrumentation.vertx.sql.VertxSqlClientUtil.setSqlConnectOptions;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.SqlConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PoolInstrumentation implements TypeInstrumentation {

  private static final Logger logger = Logger.getLogger(PoolInstrumentation.class.getName());

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
        PoolInstrumentation.class.getName() + "$PoolAdvice");

    // In 3.9, getConnection only has callback-based version, not Future-based
    transformer.applyAdviceToMethod(
        named("getConnection")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        PoolInstrumentation.class.getName() + "$GetConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class PoolAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static CallDepth onEnter(@Advice.Argument(1) SqlConnectOptions sqlConnectOptions) {
      CallDepth callDepth = CallDepth.forClass(Pool.class);
      if (callDepth.getAndIncrement() > 0) {
        return callDepth;
      }

      // set connection options to ThreadLocal, they will be read in SqlClientBase constructor
      setSqlConnectOptions(sqlConnectOptions);
      return callDepth;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return Pool pool,
        @Advice.Argument(1) SqlConnectOptions sqlConnectOptions,
        @Advice.Enter CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      setPoolConnectOptions(pool, sqlConnectOptions);
      setSqlConnectOptions(null);
    }
  }

  @SuppressWarnings("unused")
  public static class GetConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Pool pool, @Advice.Argument(0) Handler<AsyncResult<SqlConnection>> handler) {
      // In 3.9, we need to wrap the callback handler to attach connection options
      SqlConnectOptions sqlConnectOptions = getPoolSqlConnectOptions(pool);
      if (sqlConnectOptions != null) {
        setSqlConnectOptions(sqlConnectOptions);

        if (logger.isLoggable(Level.INFO)) {
          logger.info("Getting connection from pool for host: " + sqlConnectOptions.getHost());
        }
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      setSqlConnectOptions(null);
    }
  }
}
