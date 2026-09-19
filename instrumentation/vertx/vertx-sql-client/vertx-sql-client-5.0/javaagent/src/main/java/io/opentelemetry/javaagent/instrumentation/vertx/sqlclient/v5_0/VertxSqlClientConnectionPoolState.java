/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentAcquisition;
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
    VertxSqlClientConstructionState state =
        VertxSqlClientSingletons.currentConstructionState().get();
    if (state != null && state.getSupplier() != null) {
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
  public static Acquisition createAcquisition(ConnectionPool<?> pool, Completable<?> handler) {
    Submission current = currentSubmission().get();
    if (current != null && current.pool == pool && !current.claimed) {
      current.claimed = true;
      return new Acquisition(handler, current.query);
    }
    return null;
  }

  public static void attachWaiter(PoolWaiter<?> waiter, Completable<?> handler) {
    Acquisition current = currentAcquisition().get();
    // The acquisition advice restores its previous value. Claim this one-shot handoff so only the
    // waiter created for this handler consumes the query.
    if (current != null && current.handler == handler && !current.claimed) {
      current.claimed = true;
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

    private Submission(ConnectionPool<?> pool, VertxSqlClientQueryState query) {
      this.pool = pool;
      this.query = query;
    }
  }

  public static final class Acquisition {
    private final Completable<?> handler;
    private final VertxSqlClientQueryState query;
    private boolean claimed;

    private Acquisition(Completable<?> handler, VertxSqlClientQueryState query) {
      this.handler = handler;
      this.query = query;
    }
  }

  private VertxSqlClientConnectionPoolState() {}
}
