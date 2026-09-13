/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.ConnectionAttempt;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolWaiter;
import javax.annotation.Nullable;

public final class VertxSqlClientConnectionPoolState {
  private static final VirtualField<ConnectionPool<?>, VertxSqlClientInfo> POOL_SUPPLIER =
      VirtualField.find(ConnectionPool.class, VertxSqlClientInfo.class);
  private static final VirtualField<PoolWaiter<?>, VertxSqlClientQueryState> WAITER_QUERY =
      VirtualField.find(PoolWaiter.class, VertxSqlClientQueryState.class);
  private static final ThreadLocal<VertxSqlClientQueryState> pendingQuery = new ThreadLocal<>();
  private static final ThreadLocal<ConnectionAttempt> connectionAttempt = new ThreadLocal<>();

  public static void attachSupplier(ConnectionPool<?> pool) {
    VertxSqlClientConstructionState state = VertxSqlClientSingletons.getConstructionState();
    if (state != null && state.getSupplier() != null) {
      POOL_SUPPLIER.set(pool, state.getInfo());
    }
  }

  @Nullable
  public static VertxSqlClientQueryState enterQuery(Object command) {
    VertxSqlClientQueryState previous = pendingQuery.get();
    Context context = VertxSqlClientSingletons.getCommandContext(command);
    VertxSqlClientQueryState query = context != null ? context.get(QUERY_STATE) : null;
    setQuery(query);
    return previous;
  }

  public static void setQuery(@Nullable VertxSqlClientQueryState value) {
    if (value == null) {
      pendingQuery.remove();
    } else {
      pendingQuery.set(value);
    }
  }

  public static void attachWaiter(PoolWaiter<?> waiter) {
    VertxSqlClientQueryState query = pendingQuery.get();
    if (query != null) {
      pendingQuery.remove();
      WAITER_QUERY.set(waiter, query);
    }
  }

  @Nullable
  public static ConnectionAttempt enterConnection(ConnectionPool<?> pool, PoolWaiter<?> waiter) {
    ConnectionAttempt previous = connectionAttempt.get();
    VertxSqlClientInfo supplier = POOL_SUPPLIER.get(pool);
    if (supplier == null) {
      connectionAttempt.remove();
    } else {
      // The combiner may start a queued replacement before delivering another waiter's failure.
      VertxSqlClientQueryState query = WAITER_QUERY.get(waiter);
      WAITER_QUERY.set(waiter, null);
      connectionAttempt.set(new ConnectionAttempt(supplier.getDbSystemName(), query));
    }
    return previous;
  }

  @Nullable
  public static ConnectionAttempt getConnectionAttempt() {
    return connectionAttempt.get();
  }

  public static void exitConnection(
      @Nullable ConnectionAttempt previous, @Nullable Throwable throwable) {
    ConnectionAttempt current = connectionAttempt.get();
    if (previous == null) {
      connectionAttempt.remove();
    } else {
      connectionAttempt.set(previous);
    }
    if (current != null) {
      current.end(throwable);
    }
  }

  private VertxSqlClientConnectionPoolState() {}
}
