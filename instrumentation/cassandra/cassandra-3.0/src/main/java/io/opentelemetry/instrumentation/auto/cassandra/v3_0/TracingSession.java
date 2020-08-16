/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.cassandra.v3_0;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.opentelemetry.instrumentation.auto.cassandra.v3_0.CassandraDatabaseClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

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
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TracingSession implements Session {
  private static final ExecutorService EXECUTOR_SERVICE =
      Executors.newCachedThreadPool(
          new DaemonThreadFactory("opentelemetry-cassandra-session-executor"));

  private final Session session;

  public TracingSession(Session session) {
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
          public Session apply(Session session) {
            return new TracingSession(session);
          }
        },
        directExecutor());
  }

  @Override
  public ResultSet execute(String query) {
    Objects.requireNonNull(query);
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      try {
        ResultSet resultSet = session.execute(query);
        TRACER.onResponse(span, resultSet.getExecutionInfo());
        return resultSet;
      } catch (RuntimeException e) {
        TRACER.endExceptionally(span, e);
        throw e;
      } finally {
        TRACER.end(span);
      }
    }
  }

  @Override
  public ResultSet execute(String query, Object... values) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      try {
        ResultSet resultSet = session.execute(query, values);
        TRACER.onResponse(span, resultSet.getExecutionInfo());
        return resultSet;
      } catch (RuntimeException e) {
        TRACER.endExceptionally(span, e);
        throw e;
      } finally {
        TRACER.end(span);
      }
    }
  }

  @Override
  public ResultSet execute(String query, Map<String, Object> values) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      try {
        ResultSet resultSet = session.execute(query, values);
        TRACER.onResponse(span, resultSet.getExecutionInfo());
        return resultSet;
      } catch (RuntimeException e) {
        TRACER.endExceptionally(span, e);
        throw e;
      } finally {
        TRACER.end(span);
      }
    }
  }

  @Override
  public ResultSet execute(Statement statement) {
    String query = getQuery(statement);
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      try {
        ResultSet resultSet = session.execute(statement);
        TRACER.onResponse(span, resultSet.getExecutionInfo());
        return resultSet;
      } catch (RuntimeException e) {
        TRACER.endExceptionally(span, e);
        throw e;
      } finally {
        TRACER.end(span);
      }
    }
  }

  @Override
  public ResultSetFuture executeAsync(String query) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      ResultSetFuture future = session.executeAsync(query);
      future.addListener(createListener(span, future), EXECUTOR_SERVICE);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(String query, Object... values) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(span, future), EXECUTOR_SERVICE);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(String query, Map<String, Object> values) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      ResultSetFuture future = session.executeAsync(query, values);
      future.addListener(createListener(span, future), EXECUTOR_SERVICE);

      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(Statement statement) {
    String query = getQuery(statement);
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      ResultSetFuture future = session.executeAsync(statement);
      future.addListener(createListener(span, future), EXECUTOR_SERVICE);

      return future;
    }
  }

  @Override
  public PreparedStatement prepare(String query) {
    return session.prepare(query);
  }

  @Override
  public PreparedStatement prepare(RegularStatement statement) {
    return session.prepare(statement);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(String query) {
    return session.prepareAsync(query);
  }

  @Override
  public ListenableFuture<PreparedStatement> prepareAsync(RegularStatement statement) {
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

  private static String getQuery(Statement statement) {
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
        try (Scope ignored = currentContextWith(span)) {
          ResultSet resultSet = future.get();
          TRACER.onResponse(span, resultSet.getExecutionInfo());
        } catch (InterruptedException | ExecutionException e) {
          TRACER.endExceptionally(span, e);
        } finally {
          TRACER.end(span);
        }
      }
    };
  }
}
