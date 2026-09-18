/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentAcquisition;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentConnectionAttempt;
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
        named("acquire")
            .and(takesArguments(3))
            .and(takesArgument(2, named("io.vertx.core.Completable"))),
        getClass().getName() + "$AcquireAdvice");
    transformer.applyAdviceToMethod(
        named("acquire")
            .and(takesArguments(4))
            .and(takesArgument(3, named("io.vertx.core.Completable"))),
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
      return currentAcquisition()
          .set(VertxSqlClientConnectionPoolState.createAcquisition(pool, handler));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Acquisition previous) {
      // If no waiter consumed the current acquisition, this discards that failed handoff.
      currentAcquisition().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class AcquireWithListenerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Acquisition onEnter(
        @Advice.This ConnectionPool<?> pool, @Advice.Argument(3) Completable<?> handler) {
      return currentAcquisition()
          .set(VertxSqlClientConnectionPoolState.createAcquisition(pool, handler));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Acquisition previous) {
      // If no waiter consumed the current acquisition, this discards that failed handoff.
      currentAcquisition().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ConnectionAttempt onEnter(
        @Advice.This ConnectionPool<?> pool, @Advice.Argument(1) PoolWaiter<?> waiter) {
      return currentConnectionAttempt()
          .set(VertxSqlClientConnectionPoolState.createConnectionAttempt(pool, waiter));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable ConnectionAttempt previous,
        @Advice.Thrown @Nullable Throwable throwable) {
      ConnectionAttempt current = currentConnectionAttempt().get();
      currentConnectionAttempt().restore(previous);
      if (current != null) {
        current.end(throwable);
      }
    }
  }
}
