/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.internal.core.cql.reactive.DefaultReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.annotation.Nullable;

final class TracingCqlSession {
  private final Instrumenter<CassandraRequest, ExecutionInfo> instrumenter;

  TracingCqlSession(Instrumenter<CassandraRequest, ExecutionInfo> instrumenter) {
    this.instrumenter = instrumenter;
  }

  CqlSession wrapSession(CqlSession session) {
    if (session == null) {
      return null;
    }

    List<Class<?>> interfaces = new ArrayList<>();
    Class<?> clazz = session.getClass();
    while (clazz != Object.class) {
      interfaces.addAll(Arrays.asList(clazz.getInterfaces()));
      clazz = clazz.getSuperclass();
    }
    return (CqlSession)
        Proxy.newProxyInstance(
            session.getClass().getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
              if ("execute".equals(method.getName()) && method.getParameterCount() == 1) {
                if (method.getParameterTypes()[0] == String.class) {
                  String query = (String) args[0];
                  return execute(session, query);
                } else if (method.getParameterTypes()[0] == Statement.class) {
                  Statement<?> statement = (Statement<?>) args[0];
                  return execute(session, statement);
                }
              } else if ("executeAsync".equals(method.getName())
                  && method.getParameterCount() == 1) {
                if (method.getParameterTypes()[0] == String.class) {
                  String query = (String) args[0];
                  return executeAsync(session, query);
                } else if (method.getParameterTypes()[0] == Statement.class) {
                  Statement<?> statement = (Statement<?>) args[0];
                  return executeAsync(session, statement);
                }
              } else if ("executeReactive".equals(method.getName())
                  && method.getParameterCount() == 1) {
                if (method.getParameterTypes()[0] == String.class) {
                  String query = (String) args[0];
                  return executeReactive(session, query);
                } else if (method.getParameterTypes()[0] == Statement.class) {
                  Statement<?> statement = (Statement<?>) args[0];
                  return executeReactive(session, statement);
                }
              }

              return method.invoke(session, args);
            });
  }

  private ResultSet execute(CqlSession session, String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter.start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(query);
    } catch (Throwable exception) {
      instrumenter.end(context, request, getExecutionInfo(exception), exception);
      throw exception;
    }
    instrumenter.end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  private ResultSet execute(CqlSession session, Statement<?> statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter.start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(statement);
    } catch (Throwable exception) {
      instrumenter.end(context, request, getExecutionInfo(exception), exception);
      throw exception;
    }
    instrumenter.end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  private CompletionStage<AsyncResultSet> executeAsync(CqlSession session, Statement<?> statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    return executeAsync(request, () -> session.executeAsync(statement));
  }

  private CompletionStage<AsyncResultSet> executeAsync(CqlSession session, String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    return executeAsync(request, () -> session.executeAsync(query));
  }

  private CompletionStage<AsyncResultSet> executeAsync(
      CassandraRequest request, Supplier<CompletionStage<AsyncResultSet>> query) {
    Context parentContext = Context.current();
    Context context = instrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      CompletionStage<AsyncResultSet> stage = query.get();
      return wrap(
          stage.whenComplete(
              (asyncResultSet, throwable) ->
                  instrumenter.end(
                      context, request, getExecutionInfo(asyncResultSet, throwable), throwable)),
          parentContext);
    }
  }

  private ReactiveResultSet executeReactive(CqlSession session, String query) {
    return new DefaultReactiveResultSet(() -> executeAsync(session, query));
  }

  private ReactiveResultSet executeReactive(CqlSession session, Statement<?> statement) {
    return new DefaultReactiveResultSet(() -> executeAsync(session, statement));
  }

  private static <T> CompletableFuture<T> wrap(CompletionStage<T> future, Context context) {
    CompletableFuture<T> result = new CompletableFuture<>();
    future.whenComplete(
        (T value, Throwable throwable) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (throwable != null) {
              result.completeExceptionally(throwable);
            } else {
              result.complete(value);
            }
          }
        });

    return result;
  }

  private static String getQuery(Statement<?> statement) {
    String query = null;
    if (statement instanceof SimpleStatement) {
      query = ((SimpleStatement) statement).getQuery();
    } else if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).getPreparedStatement().getQuery();
    }

    return query == null ? "" : query;
  }

  private static ExecutionInfo getExecutionInfo(
      @Nullable AsyncResultSet asyncResultSet, @Nullable Throwable throwable) {
    if (asyncResultSet != null) {
      return asyncResultSet.getExecutionInfo();
    } else {
      return getExecutionInfo(throwable);
    }
  }

  private static ExecutionInfo getExecutionInfo(@Nullable Throwable throwable) {
    if (throwable instanceof DriverException) {
      return ((DriverException) throwable).getExecutionInfo();
    } else if (throwable != null && throwable.getCause() instanceof DriverException) {
      // TODO (trask) find out if this is needed and if so add comment explaining
      return ((DriverException) throwable.getCause()).getExecutionInfo();
    } else {
      return null;
    }
  }
}
