/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.datastax.cassandra;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.opentelemetry.auto.instrumentation.datastax.cassandra.CassandraClientDecorator.DECORATE;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TracingSession implements Session {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.cassandra-3.0");

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final Session session;

  public TracingSession(final Session session) {
    this.session = session;
  }

  @Override
  public String getLoggedKeyspace() {
    return session.getLoggedKeyspace();
  }

  @Override
  public Session init() {
    return new TracingSession(session.init());
  }

  @Override
  public ListenableFuture<Session> initAsync() {
    return Futures.transform(
        session.initAsync(),
        new Function<Session, Session>() {
          @Override
          public Session apply(final Session session) {
            return new TracingSession(session);
          }
        },
        directExecutor());
  }

  @Override
  public ResultSet execute(final String query) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      try {
        final ResultSet resultSet = session.execute(query);
        beforeSpanFinish(span, resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(span, e);
        throw e;
      } finally {
        span.end();
      }
    }
  }

  @Override
  public ResultSet execute(final String query, final Object... values) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      try {
        final ResultSet resultSet = session.execute(query, values);
        beforeSpanFinish(span, resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(span, e);
        throw e;
      } finally {
        span.end();
      }
    }
  }

  @Override
  public ResultSet execute(final String query, final Map<String, Object> values) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      try {
        final ResultSet resultSet = session.execute(query, values);
        beforeSpanFinish(span, resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(span, e);
        throw e;
      } finally {
        span.end();
      }
    }
  }

  @Override
  public ResultSet execute(final Statement statement) {
    final String query = getQuery(statement);
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      try {
        final ResultSet resultSet = session.execute(statement);
        beforeSpanFinish(span, resultSet);
        return resultSet;
      } catch (final RuntimeException e) {
        beforeSpanFinish(span, e);
        throw e;
      } finally {
        span.end();
      }
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      final ResultSetFuture future = session.executeAsync(query);
      future.addListener(createListener(span, future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query, final Object... values) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      final ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(span, future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final String query, final Map<String, Object> values) {
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      final ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(span, future), executorService);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(final Statement statement) {
    final String query = getQuery(statement);
    final Span span = startSpan(query);
    try (final Scope scope = TRACER.withSpan(span)) {
      final ResultSetFuture future = session.executeAsync(statement);
      future.addListener(createListener(span, future), executorService);

      return future;
    }
  }

  @Override
  public PreparedStatement prepare(final String query) {
    return session.prepare(query);
  }

  @Override
  public PreparedStatement prepare(final RegularStatement statement) {
    return session.prepare(statement);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(final String query) {
    return session.prepareAsync(query);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(final RegularStatement statement) {
    return session.prepareAsync(statement);
  }

  @Override
  public CloseFuture closeAsync() {
    return session.closeAsync();
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  public Cluster getCluster() {
    return session.getCluster();
  }

  @Override
  public State getState() {
    return session.getState();
  }

  private static String getQuery(final Statement statement) {
    String query = null;
    if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).preparedStatement().getQueryString();
    } else if (statement instanceof RegularStatement) {
      query = ((RegularStatement) statement).getQueryString();
    }

    return query == null ? "" : query;
  }

  private static Runnable createListener(final Span span, final ResultSetFuture future) {
    return new Runnable() {
      @Override
      public void run() {
        try (final Scope scope = TRACER.withSpan(span)) {
          beforeSpanFinish(span, future.get());
        } catch (final InterruptedException | ExecutionException e) {
          beforeSpanFinish(span, e);
        } finally {
          span.end();
        }
      }
    };
  }

  private Span startSpan(final String query) {
    final Span span = TRACER.spanBuilder(query).setSpanKind(CLIENT).startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, session);
    DECORATE.onStatement(span, query);
    return span;
  }

  private static void beforeSpanFinish(final Span span, final ResultSet resultSet) {
    DECORATE.onResponse(span, resultSet);
    DECORATE.beforeFinish(span);
  }

  private static void beforeSpanFinish(final Span span, final Exception e) {
    DECORATE.onError(span, e);
    DECORATE.beforeFinish(span);
  }
}
