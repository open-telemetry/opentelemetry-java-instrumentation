/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Completable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.internal.CloseFuture;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.NetClientOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.spi.PgDriver;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.internal.command.CommandBase;
import io.vertx.sqlclient.internal.pool.CloseablePool;
import io.vertx.sqlclient.internal.pool.PoolImpl;
import io.vertx.sqlclient.spi.ConnectionFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class TestPgDriver extends PgDriver {
  private final Function<PgConnectOptions, Future<?>> connectionProvider;
  private final Runnable beforeSchedule;
  private final Consumer<Throwable> afterCompletion;
  private PoolImpl pool;
  private CloseFuture closeFuture;

  static TestPgDriver create(Function<PgConnectOptions, Future<?>> connectionProvider) {
    return create(connectionProvider, () -> {}, ignored -> {});
  }

  static TestPgDriver create(
      Function<PgConnectOptions, Future<?>> connectionProvider,
      Runnable beforeSchedule,
      Consumer<Throwable> afterCompletion) {
    return new TestPgDriver(connectionProvider, beforeSchedule, afterCompletion);
  }

  private TestPgDriver(
      Function<PgConnectOptions, Future<?>> connectionProvider,
      Runnable beforeSchedule,
      Consumer<Throwable> afterCompletion) {
    this.connectionProvider = connectionProvider;
    this.beforeSchedule = beforeSchedule;
    this.afterCompletion = afterCompletion;
  }

  @Override
  public Pool newPool(
      Vertx vertx,
      Supplier<Future<PgConnectOptions>> databases,
      PoolOptions poolOptions,
      NetClientOptions transportOptions,
      Handler<SqlConnection> connectHandler,
      CloseFuture closeFuture) {
    VertxInternal internal = (VertxInternal) vertx;
    ConnectionFactory<PgConnectOptions> factory = createConnectionFactory(vertx, transportOptions);
    PoolImpl pool =
        new PoolImpl(
            internal,
            this,
            false,
            poolOptions,
            null,
            null,
            context -> factory.connect(context, databases.get()),
            connectHandler,
            closeFuture) {
          @Override
          public <R> void schedule(CommandBase<R> command, Completable<R> handler) {
            beforeSchedule.run();
            super.schedule(
                command,
                (result, failure) -> {
                  try {
                    handler.complete(result, failure);
                  } finally {
                    afterCompletion.accept(failure);
                  }
                });
          }
        };
    pool.init();
    closeFuture.add(factory);
    this.pool = pool;
    this.closeFuture = closeFuture;
    return new CloseablePool(internal, closeFuture, pool);
  }

  Future<Void> closeAfterSupplierThrow() {
    assertThat(closeFuture.remove(pool)).isTrue();
    // A synchronous supplier throw leaves a core pool slot whose close future cannot complete.
    pool.close((ignored, failure) -> {});
    return closeFuture.close();
  }

  @Override
  public ConnectionFactory<PgConnectOptions> createConnectionFactory(
      Vertx vertx, NetClientOptions transportOptions) {
    return new ConnectionFactory<PgConnectOptions>() {
      @Override
      public Future<SqlConnection> connect(Context context, PgConnectOptions options) {
        return propagateFailure(connectionProvider.apply(options));
      }

      @Override
      public void close(Completable<Void> completion) {
        completion.succeed();
      }
    };
  }

  private static <T> Future<T> propagateFailure(Future<?> future) {
    return future.transform(
        result ->
            Future.failedFuture(
                result.failed()
                    ? result.cause()
                    : new AssertionError("Test connection unexpectedly succeeded")));
  }
}
