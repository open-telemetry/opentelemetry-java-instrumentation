/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.proxy.callback.ProxyConfigHolder;
import io.r2dbc.proxy.callback.ProxyFactory;
import io.r2dbc.proxy.callback.QueriesExecutionContext;
import io.r2dbc.proxy.core.ConnectionInfo;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.StatementInfo;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.Wrapped;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class FilterAwareProxyFactory implements ProxyFactory {
  private final ProxyFactory delegate;

  public static void configure(ProxyConfig config) {
    ProxyFactory delegate = config.getProxyFactory();
    config.setProxyFactoryFactory(ignored -> new FilterAwareProxyFactory(delegate));
  }

  private FilterAwareProxyFactory(ProxyFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public ConnectionFactory wrapConnectionFactory(ConnectionFactory factory) {
    return delegate.wrapConnectionFactory(factory);
  }

  @Override
  public Connection wrapConnection(Connection connection, ConnectionInfo info) {
    return delegate.wrapConnection(connection, info);
  }

  @Override
  public Batch wrapBatch(Batch batch, ConnectionInfo info) {
    return delegate.wrapBatch(batch, info);
  }

  @Override
  public Statement wrapStatement(
      Statement statement, StatementInfo statementInfo, ConnectionInfo info) {
    return delegate.wrapStatement(statement, statementInfo, info);
  }

  @Override
  public Result wrapResult(
      Result result, QueryExecutionInfo info, QueriesExecutionContext context) {
    return new FilterAwareResult(delegate.wrapResult(result, info, context), this, info, context);
  }

  @Override
  public Row wrapRow(Row row, QueryExecutionInfo info) {
    return delegate.wrapRow(row, info);
  }

  @Override
  public Result.RowSegment wrapRowSegment(Result.RowSegment segment, QueryExecutionInfo info) {
    return delegate.wrapRowSegment(segment, info);
  }

  private static final class FilterAwareResult
      implements Result, Wrapped<Result>, ProxyConfigHolder {
    private final Result delegate;
    private final FilterAwareProxyFactory factory;
    private final QueryExecutionInfo info;
    private final QueriesExecutionContext context;

    private FilterAwareResult(
        Result delegate,
        FilterAwareProxyFactory factory,
        QueryExecutionInfo info,
        QueriesExecutionContext context) {
      this.delegate = delegate;
      this.factory = factory;
      this.info = info;
      this.context = context;
    }

    @Override
    public Publisher<Long> getRowsUpdated() {
      // The Publisher signature is erased; forward SPI 0.9 Integer and SPI 1.0 Long counts
      // unchanged.
      return delegate.getRowsUpdated();
    }

    @Override
    public <T> Publisher<T> map(BiFunction<Row, RowMetadata, ? extends T> mapping) {
      return delegate.map(mapping);
    }

    @Override
    public <T> Publisher<T> map(Function<? super Readable, ? extends T> mapping) {
      return delegate.map(mapping);
    }

    @Override
    public Result filter(Predicate<Segment> predicate) {
      // r2dbc-proxy returns the driver's filtered Result without wrapping it. Reuse the query
      // context so completion, error and cancellation still finish the original execution.
      return factory.wrapResult(delegate.filter(predicate), info, context);
    }

    @Override
    public <T> Publisher<T> flatMap(Function<Segment, ? extends Publisher<? extends T>> mapping) {
      return delegate.flatMap(mapping);
    }

    @Override
    public Result unwrap() {
      return (Result) ((Wrapped<?>) delegate).unwrap();
    }

    @Override
    @Nullable
    public <E> E unwrap(Class<E> type) {
      return ((Wrapped<?>) delegate).unwrap(type);
    }

    @Override
    public ProxyConfig getProxyConfig() {
      return ((ProxyConfigHolder) delegate).getProxyConfig();
    }
  }
}
