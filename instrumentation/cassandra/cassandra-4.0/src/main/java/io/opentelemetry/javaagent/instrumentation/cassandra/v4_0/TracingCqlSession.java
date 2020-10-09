/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.cassandra.v4_0;

import static io.opentelemetry.instrumentation.auto.cassandra.v4_0.CassandraDatabaseClientTracer.TRACER;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PrepareRequest;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metrics.Metrics;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public class TracingCqlSession implements CqlSession {
  private final CqlSession session;

  public TracingCqlSession(CqlSession session) {
    this.session = session;
  }

  @Override
  @NonNull
  public PreparedStatement prepare(@NonNull SimpleStatement statement) {
    return session.prepare(statement);
  }

  @Override
  @NonNull
  public PreparedStatement prepare(@NonNull String query) {
    return session.prepare(query);
  }

  @Override
  @NonNull
  public PreparedStatement prepare(@NonNull PrepareRequest request) {
    return session.prepare(request);
  }

  @Override
  @NonNull
  public CompletionStage<PreparedStatement> prepareAsync(@NonNull SimpleStatement statement) {
    return session.prepareAsync(statement);
  }

  @Override
  @NonNull
  public CompletionStage<PreparedStatement> prepareAsync(@NonNull String query) {
    return session.prepareAsync(query);
  }

  @Override
  @NonNull
  public CompletionStage<PreparedStatement> prepareAsync(PrepareRequest request) {
    return session.prepareAsync(request);
  }

  @Override
  @NonNull
  public String getName() {
    return session.getName();
  }

  @Override
  @NonNull
  public Metadata getMetadata() {
    return session.getMetadata();
  }

  @Override
  public boolean isSchemaMetadataEnabled() {
    return session.isSchemaMetadataEnabled();
  }

  @Override
  @NonNull
  public CompletionStage<Metadata> setSchemaMetadataEnabled(@Nullable Boolean newValue) {
    return session.setSchemaMetadataEnabled(newValue);
  }

  @Override
  @NonNull
  public CompletionStage<Metadata> refreshSchemaAsync() {
    return session.refreshSchemaAsync();
  }

  @Override
  @NonNull
  public Metadata refreshSchema() {
    return session.refreshSchema();
  }

  @Override
  @NonNull
  public CompletionStage<Boolean> checkSchemaAgreementAsync() {
    return session.checkSchemaAgreementAsync();
  }

  @Override
  public boolean checkSchemaAgreement() {
    return session.checkSchemaAgreement();
  }

  @Override
  @NonNull
  public DriverContext getContext() {
    return session.getContext();
  }

  @Override
  @NonNull
  public Optional<CqlIdentifier> getKeyspace() {
    return session.getKeyspace();
  }

  @Override
  @NonNull
  public Optional<Metrics> getMetrics() {
    return session.getMetrics();
  }

  @Override
  @Nullable
  public <RequestT extends Request, ResultT> ResultT execute(
      @NonNull RequestT request, @NonNull GenericType<ResultT> resultType) {
    return session.execute(request, resultType);
  }

  @Override
  @NonNull
  public CompletionStage<Void> closeFuture() {
    return session.closeFuture();
  }

  @Override
  public boolean isClosed() {
    return session.isClosed();
  }

  @Override
  @NonNull
  public CompletionStage<Void> closeAsync() {
    return session.closeAsync();
  }

  @Override
  @NonNull
  public CompletionStage<Void> forceCloseAsync() {
    return session.forceCloseAsync();
  }

  @Override
  public void close() {
    session.close();
  }

  @Override
  @NonNull
  public ResultSet execute(@NonNull Statement<?> statement) {
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
  @NonNull
  public ResultSet execute(@NonNull String query) {

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
  @NonNull
  public CompletionStage<AsyncResultSet> executeAsync(@NonNull Statement<?> statement) {
    String query = getQuery(statement);

    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      CompletionStage<AsyncResultSet> stage = session.executeAsync(statement);
      return stage.whenComplete(
          (asyncResultSet, throwable) -> {
            if (throwable != null) {
              TRACER.endExceptionally(span, throwable);
            } else {
              TRACER.onResponse(span, asyncResultSet.getExecutionInfo());
              TRACER.end(span);
            }
          });
    }
  }

  @Override
  @NonNull
  public CompletionStage<AsyncResultSet> executeAsync(@NonNull String query) {
    Span span = TRACER.startSpan(session, query);
    try (Scope ignored = TRACER.startScope(span)) {
      CompletionStage<AsyncResultSet> stage = session.executeAsync(query);
      return stage.whenComplete(
          (asyncResultSet, throwable) -> {
            if (throwable != null) {
              TRACER.endExceptionally(span, throwable);
            } else {
              TRACER.onResponse(span, asyncResultSet.getExecutionInfo());
              TRACER.end(span);
            }
          });
    }
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
}
