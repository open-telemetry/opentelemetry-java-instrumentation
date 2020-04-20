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
package io.opentelemetry.auto.instrumentation.cassandra.v4_0;

import static io.opentelemetry.auto.instrumentation.cassandra.v4_0.CassandraClientDecorator.DECORATE;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

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
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TracingCqlSession implements CqlSession {
  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.cassandra-4.0");

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final CqlSession session;

  public TracingCqlSession(final CqlSession session) {
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
    final String query = getQuery(statement);
    final Span span = startSpan(query);

    try (final Scope scope = currentContextWith(span)) {
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
  @NonNull
  public ResultSet execute(@NonNull String query) {
    final Span span = startSpan(query);
    try (final Scope scope = currentContextWith(span)) {
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
  @NonNull
  public CompletionStage<AsyncResultSet> executeAsync(@NonNull Statement<?> statement) {
    final String query = getQuery(statement);
    final Span span = startSpan(query);

    try (final Scope scope = currentContextWith(span)) {
      final CompletionStage<AsyncResultSet> stage = session.executeAsync(statement);
      return stage.whenComplete(
          (asyncResultSet, throwable) -> {
            if (throwable != null) {
              beforeSpanFinish(span, throwable);
            } else {
              beforeSpanFinish(span, asyncResultSet);
            }
            span.end();
          });
    }
  }

  @Override
  @NonNull
  public CompletionStage<AsyncResultSet> executeAsync(@NonNull String query) {
    final Span span = startSpan(query);
    try (final Scope scope = currentContextWith(span)) {
      final CompletionStage<AsyncResultSet> stage = session.executeAsync(query);
      return stage.whenComplete(
          (asyncResultSet, throwable) -> {
            if (throwable != null) {
              beforeSpanFinish(span, throwable);
            } else {
              beforeSpanFinish(span, asyncResultSet);
            }
            span.end();
          });
    }
  }

  private static String getQuery(final Statement<?> statement) {
    String query = null;
    if (statement instanceof SimpleStatement) {
      query = ((SimpleStatement) statement).getQuery();
    } else if (statement instanceof BoundStatement) {
      query = ((BoundStatement) statement).getPreparedStatement().getQuery();
    }

    return query == null ? "" : query;
  }

  private Span startSpan(final String query) {
    final Span span = TRACER.spanBuilder(query).setSpanKind(CLIENT).startSpan();
    DECORATE.afterStart(span);
    DECORATE.onConnection(span, session);
    DECORATE.onStatement(span, query);
    return span;
  }

  private static void beforeSpanFinish(final Span span, final ResultSet resultSet) {
    if (resultSet != null) {
      DECORATE.onResponse(span, resultSet.getExecutionInfo());
    }
    DECORATE.beforeFinish(span);
  }

  private static void beforeSpanFinish(final Span span, final Throwable e) {
    DECORATE.onError(span, e);
    DECORATE.beforeFinish(span);
  }

  private void beforeSpanFinish(Span span, AsyncResultSet asyncResultSet) {
    if (asyncResultSet != null) {
      DECORATE.onResponse(span, asyncResultSet.getExecutionInfo());
    }
    DECORATE.beforeFinish(span);
  }
}
