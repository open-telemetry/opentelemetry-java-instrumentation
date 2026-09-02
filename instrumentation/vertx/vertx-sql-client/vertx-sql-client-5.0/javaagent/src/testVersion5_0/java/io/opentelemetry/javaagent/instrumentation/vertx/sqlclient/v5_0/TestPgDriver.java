/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import io.vertx.core.Completable;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.spi.PgDriver;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.spi.ConnectionFactory;
import java.util.function.Function;

final class TestPgDriver extends PgDriver {
  private final Function<PgConnectOptions, Future<?>> connectionProvider;

  static PgDriver create(Function<PgConnectOptions, Future<?>> connectionProvider) {
    return new TestPgDriver(connectionProvider);
  }

  private TestPgDriver(Function<PgConnectOptions, Future<?>> connectionProvider) {
    this.connectionProvider = connectionProvider;
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
