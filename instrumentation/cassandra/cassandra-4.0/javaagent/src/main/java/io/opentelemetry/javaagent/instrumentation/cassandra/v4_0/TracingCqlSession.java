/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraSingletons.instrumenter;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.PrepareRequest;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metrics.Metrics;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class TracingCqlSession implements CqlSession {
  private final CqlSession session;

  public TracingCqlSession(CqlSession session) {
    this.session = session;
  }

  @Override
  public PreparedStatement prepare(SimpleStatement statement) {
    return session.prepare(statement);
  }

  @Override
  public PreparedStatement prepare(String query) {
    return session.prepare(query);
  }

  @Override
  public PreparedStatement prepare(PrepareRequest request) {
    return session.prepare(request);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(SimpleStatement statement) {
    return session.prepareAsync(statement);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(String query) {
    return session.prepareAsync(query);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(PrepareRequest request) {
    return session.prepareAsync(request);
  }

  @Override
  public String getName() {
    return session.getName();
  }

  @Override
  public Metadata getMetadata() {
    return session.getMetadata();
  }

  @Override
  public boolean isSchemaMetadataEnabled() {
    return session.isSchemaMetadataEnabled();
  }

  @Override
  public CompletionStage<Metadata> setSchemaMetadataEnabled(@Nullable Boolean newValue) {
    return session.setSchemaMetadataEnabled(newValue);
  }

  @Override
  public CompletionStage<Metadata> refreshSchemaAsync() {
    return session.refreshSchemaAsync();
  }

  @Override
  public Metadata refreshSchema() {
    return session.refreshSchema();
  }

  @Override
  public CompletionStage<Boolean> checkSchemaAgreementAsync() {
    return session.checkSchemaAgreementAsync();
  }

  @Override
  public boolean checkSchemaAgreement() {
    return session.checkSchemaAgreement();
  }

  @Override
  public DriverContext getContext() {
    return session.getContext();
  }

  @Override
  public Optional<CqlIdentifier> getKeyspace() {
    return session.getKeyspace();
  }

  @Override
  public Optional<Metrics> getMetrics() {
    return session.getMetrics();
  }

  @Override
  public CompletionStage<Void> closeFuture() {
    return session.closeFuture();
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  public CompletionStage<Void> closeAsync() {
    return session.closeAsync();
  }

  @Override
  public CompletionStage<Void> forceCloseAsync() {
    return session.forceCloseAsync();
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  @Nullable
  public <REQUEST extends Request, RESULT> RESULT execute(
      REQUEST request, GenericType<RESULT> resultType) {
    return session.execute(request, resultType);
  }

  @Override
  public ResultSet execute(String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(query);
    } catch (RuntimeException e) {
      instrumenter().end(context, request, getExecutionInfo(e), e);
      throw e;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public ResultSet execute(Statement<?> statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    Context context = instrumenter().start(Context.current(), request);
    ResultSet resultSet;
    try (Scope ignored = context.makeCurrent()) {
      resultSet = session.execute(statement);
    } catch (RuntimeException e) {
      instrumenter().end(context, request, getExecutionInfo(e), e);
      throw e;
    }
    instrumenter().end(context, request, resultSet.getExecutionInfo(), null);
    return resultSet;
  }

  @Override
  public CompletionStage<AsyncResultSet> executeAsync(Statement<?> statement) {
    String query = getQuery(statement);
    CassandraRequest request = CassandraRequest.create(session, query);
    return executeAsync(request, () -> session.executeAsync(statement));
  }

  @Override
  public CompletionStage<AsyncResultSet> executeAsync(String query) {
    CassandraRequest request = CassandraRequest.create(session, query);
    return executeAsync(request, () -> session.executeAsync(query));
  }

  private static CompletionStage<AsyncResultSet> executeAsync(
      CassandraRequest request, Supplier<CompletionStage<AsyncResultSet>> query) {
    Context parentContext = Context.current();
    Context context = instrumenter().start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      CompletionStage<AsyncResultSet> stage = query.get();
      return wrap(
          stage.whenComplete(
              (asyncResultSet, throwable) ->
                  instrumenter()
                      .end(
                          context,
                          request,
                          getExecutionInfo(asyncResultSet, throwable),
                          throwable)),
          parentContext);
    }
  }

  static <T> CompletableFuture<T> wrap(CompletionStage<T> future, Context context) {
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
