/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v3_0.CassandraInstrumenters.instrumenter;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.CloseFuture;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;

public class TracingSession implements Session {

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
    return Futures.transform(session.initAsync(), TracingSession::new, Runnable::run);
  }

  @Override
  public ResultSet execute(String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(query);
    } catch (Throwable t) {
      instrumenter().end(context, request, null, t);
      throw t;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public ResultSet execute(String query, Object... values) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(query, values);
    } catch (Throwable t) {
      instrumenter().end(context, request, null, t);
      throw t;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public ResultSet execute(String query, Map<String, Object> values) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(query, values);
    } catch (Throwable t) {
      instrumenter().end(context, request, null, t);
      throw t;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public ResultSet execute(Statement statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(statement);
    } catch (Throwable t) {
      instrumenter().end(context, request, null, t);
      throw t;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public ResultSetFuture executeAsync(String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    try (Scope ignored = context.makeCurrent()) {
      ResultSetFuture future = session.executeAsync(query);
      addCallbackToEndSpan(future, context, request);
      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(String query, Object... values) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    try (Scope ignored = context.makeCurrent()) {
      ResultSetFuture future = session.executeAsync(query, values);
      addCallbackToEndSpan(future, context, request);
      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(String query, Map<String, Object> values) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    try (Scope ignored = context.makeCurrent()) {
      ResultSetFuture future = session.executeAsync(query, values);
      addCallbackToEndSpan(future, context, request);
      return future;
    }
  }

  @Override
  public ResultSetFuture executeAsync(Statement statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    try (Scope ignored = context.makeCurrent()) {
      ResultSetFuture future = session.executeAsync(statement);
      addCallbackToEndSpan(future, context, request);
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

  private static void addCallbackToEndSpan(
      ResultSetFuture future, Context context, CassandraRequest request) {
    Futures.addCallback(
        future,
        new FutureCallback<ResultSet>() {
          @Override
          public void onSuccess(ResultSet resultSet) {
            instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
          }

          @Override
          public void onFailure(Throwable t) {
            instrumenter().end(context, request, null, t);
          }
        },
        Runnable::run);
  }
}
