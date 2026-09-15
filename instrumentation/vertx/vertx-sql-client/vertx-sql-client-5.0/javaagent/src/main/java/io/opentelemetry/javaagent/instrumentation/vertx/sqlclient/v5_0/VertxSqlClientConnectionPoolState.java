/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientQueryState.QUERY_STATE;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
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
  private static final ScopedThreadValue<Submission> submission = new ScopedThreadValue<>();
  private static final ScopedThreadValue<Acquisition> acquisition = new ScopedThreadValue<>();
  private static final ScopedThreadValue<ConnectionAttempt> connectionAttempt =
      new ScopedThreadValue<>();

  public static void attachSupplier(ConnectionPool<?> pool) {
    VertxSqlClientConstructionState state = VertxSqlClientSingletons.getConstructionState();
    if (state != null && state.getSupplier() != null) {
      POOL_SUPPLIER.set(pool, state.getInfo());
    }
  }

  @Nullable
  public static Submission enterSubmission(ConnectionPool<?> pool, Object command) {
    Context context = VertxSqlClientSingletons.getCommandContext(command);
    VertxSqlClientQueryState query = context != null ? context.get(QUERY_STATE) : null;
    return submission.set(query != null ? new Submission(pool, query) : null);
  }

  public static void exitSubmission(@Nullable Submission previous) {
    submission.restore(previous);
  }

  @Nullable
  public static Acquisition enterAcquisition(ConnectionPool<?> pool, Completable<?> handler) {
    Submission current = submission.get();
    if (current != null && current.pool == pool && !current.claimed) {
      current.claimed = true;
      return acquisition.set(new Acquisition(handler, current.query));
    }
    return acquisition.set(null);
  }

  public static void exitAcquisition(@Nullable Acquisition previous) {
    // If no waiter consumed the current acquisition, this discards that failed handoff.
    acquisition.restore(previous);
  }

  public static void attachWaiter(PoolWaiter<?> waiter, Completable<?> handler) {
    Acquisition current = acquisition.get();
    // The acquisition advice restores its previous value. Claim this one-shot handoff so only the
    // waiter created for this handler consumes the query.
    if (current != null && current.handler == handler && !current.claimed) {
      current.claimed = true;
      WAITER_QUERY.set(waiter, current.query);
    }
  }

  @Nullable
  public static ConnectionAttempt enterConnection(ConnectionPool<?> pool, PoolWaiter<?> waiter) {
    VertxSqlClientInfo supplier = POOL_SUPPLIER.get(pool);
    if (supplier == null) {
      return connectionAttempt.set(null);
    }
    // The combiner may start a queued replacement before delivering another waiter's failure.
    VertxSqlClientQueryState query = WAITER_QUERY.get(waiter);
    WAITER_QUERY.set(waiter, null);
    return connectionAttempt.set(new ConnectionAttempt(supplier.getDbSystemName(), query));
  }

  @Nullable
  public static ConnectionAttempt getConnectionAttempt() {
    return connectionAttempt.get();
  }

  public static void exitConnection(
      @Nullable ConnectionAttempt previous, @Nullable Throwable throwable) {
    ConnectionAttempt current = connectionAttempt.get();
    connectionAttempt.restore(previous);
    if (current != null) {
      current.end(throwable);
    }
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
