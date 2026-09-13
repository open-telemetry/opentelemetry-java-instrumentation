/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientConnectionPoolState.Acquisition;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.ConnectionAttempt;
import io.vertx.core.Completable;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolWaiter;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ConnectionPoolInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.internal.pool.SimpleConnectionPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("acquire").and(takesArguments(3)), getClass().getName() + "$AcquireAdvice");
    transformer.applyAdviceToMethod(
        named("acquire").and(takesArguments(4)),
        getClass().getName() + "$AcquireWithListenerAdvice");
    transformer.applyAdviceToMethod(
        named("connect")
            .and(takesArguments(2))
            .and(takesArgument(1, named("io.vertx.core.internal.pool.PoolWaiter"))),
        getClass().getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class AcquireAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Acquisition onEnter(
        @Advice.This ConnectionPool<?> pool, @Advice.Argument(2) Completable<?> handler) {
      return VertxSqlClientConnectionPoolState.enterAcquisition(pool, handler);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Acquisition previous) {
      VertxSqlClientConnectionPoolState.setAcquisition(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class AcquireWithListenerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Acquisition onEnter(
        @Advice.This ConnectionPool<?> pool, @Advice.Argument(3) Completable<?> handler) {
      return VertxSqlClientConnectionPoolState.enterAcquisition(pool, handler);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Acquisition previous) {
      VertxSqlClientConnectionPoolState.setAcquisition(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ConnectionAttempt onEnter(
        @Advice.This ConnectionPool<?> pool, @Advice.Argument(1) PoolWaiter<?> waiter) {
      return VertxSqlClientConnectionPoolState.enterConnection(pool, waiter);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable ConnectionAttempt previous,
        @Advice.Thrown @Nullable Throwable throwable) {
      VertxSqlClientConnectionPoolState.exitConnection(previous, throwable);
    }
  }
}
