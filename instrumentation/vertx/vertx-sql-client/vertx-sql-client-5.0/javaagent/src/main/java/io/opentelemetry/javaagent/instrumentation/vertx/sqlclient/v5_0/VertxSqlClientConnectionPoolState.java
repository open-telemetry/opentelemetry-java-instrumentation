/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentSubmission;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.ConnectionAttempt;
import io.vertx.core.Completable;
import io.vertx.core.internal.pool.ConnectionPool;
import io.vertx.core.internal.pool.PoolWaiter;
import javax.annotation.Nullable;

public final class VertxSqlClientConnectionPoolState {
  private static final VirtualField<ConnectionPool<?>, VertxSqlClientInfo> POOL_SUPPLIER =
      VirtualField.find(ConnectionPool.class, VertxSqlClientInfo.class);
  private static final VirtualField<PoolWaiter<?>, VertxSqlClientQueryState> WAITER_QUERY =
      VirtualField.find(PoolWaiter.class, VertxSqlClientQueryState.class);

  public static void attachSupplier(ConnectionPool<?> pool) {
    VertxSqlClientConstructionState constructionState =
        VertxSqlClientSingletons.currentConstructionState().get();
    VertxSqlClientState state = constructionState != null ? constructionState.getState() : null;
    if (state != null && state.isSupplier()) {
      POOL_SUPPLIER.set(pool, state.getInfo());
    }
  }

  @Nullable
  public static Submission createSubmission(ConnectionPool<?> pool, Object command) {
    Context context = VertxSqlClientSingletons.getCommandContext(command);
    VertxSqlClientQueryState query = context != null ? context.get(QUERY_STATE) : null;
    return query != null ? new Submission(pool, query) : null;
  }

  @Nullable
  public static Submission beginAcquisition(ConnectionPool<?> pool, Completable<?> handler) {
    Submission current = currentSubmission().get();
    if (current != null && current.pool == pool && !current.claimed) {
      current.claimed = true;
      current.handler = handler;
      return current;
    }
    return null;
  }

  public static void attachWaiter(PoolWaiter<?> waiter, Completable<?> handler) {
    Submission current = currentSubmission().get();
    // Acquire is constructed synchronously before the pool submits work to its combiner.
    if (current != null && current.handler != null && current.handler == handler) {
      current.handler = null;
      WAITER_QUERY.set(waiter, current.query);
    }
  }

  @Nullable
  public static ConnectionAttempt createConnectionAttempt(
      ConnectionPool<?> pool, PoolWaiter<?> waiter) {
    VertxSqlClientInfo supplier = POOL_SUPPLIER.get(pool);
    if (supplier == null) {
      return null;
    }
    // The combiner may start a queued replacement before delivering another waiter's failure.
    VertxSqlClientQueryState query = WAITER_QUERY.get(waiter);
    WAITER_QUERY.set(waiter, null);
    return new ConnectionAttempt(supplier.getDbSystemName(), query);
  }

  public static final class Submission {
    private final ConnectionPool<?> pool;
    private final VertxSqlClientQueryState query;
    private boolean claimed;
    @Nullable private Completable<?> handler;

    private Submission(ConnectionPool<?> pool, VertxSqlClientQueryState query) {
      this.pool = pool;
      this.query = query;
    }

    public void endAcquisition() {
      handler = null;
    }
  }

  private VertxSqlClientConnectionPoolState() {}
}
