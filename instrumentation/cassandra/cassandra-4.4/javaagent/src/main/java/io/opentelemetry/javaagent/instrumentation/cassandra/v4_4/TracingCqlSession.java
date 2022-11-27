/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.dse.driver.internal.core.cql.reactive.DefaultReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PrepareRequest;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metrics.Metrics;
import com.datastax.oss.driver.api.core.session.Request;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;

class TracingCqlSession implements CqlSession {

  private final CqlSession originalTracingCqlSession;

  public TracingCqlSession(CqlSession session) {
    originalTracingCqlSession = session;
  }

  @Override
  public PreparedStatement prepare(SimpleStatement statement) {
    return originalTracingCqlSession.prepare(statement);
  }

  @Override
  public PreparedStatement prepare(String query) {
    return originalTracingCqlSession.prepare(query);
  }

  @Override
  public PreparedStatement prepare(PrepareRequest request) {
    return originalTracingCqlSession.prepare(request);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(SimpleStatement statement) {
    return originalTracingCqlSession.prepareAsync(statement);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(String query) {
    return originalTracingCqlSession.prepareAsync(query);
  }

  @Override
  public CompletionStage<PreparedStatement> prepareAsync(PrepareRequest request) {
    return originalTracingCqlSession.prepareAsync(request);
  }

  @Override
  public String getName() {
    return originalTracingCqlSession.getName();
  }

  @Override
  public Metadata getMetadata() {
    return originalTracingCqlSession.getMetadata();
  }

  @Override
  public boolean isSchemaMetadataEnabled() {
    return originalTracingCqlSession.isSchemaMetadataEnabled();
  }

  @Override
  public CompletionStage<Metadata> setSchemaMetadataEnabled(@Nullable Boolean newValue) {
    return originalTracingCqlSession.setSchemaMetadataEnabled(newValue);
  }

  @Override
  public CompletionStage<Metadata> refreshSchemaAsync() {
    return originalTracingCqlSession.refreshSchemaAsync();
  }

  @Override
  public Metadata refreshSchema() {
    return originalTracingCqlSession.refreshSchema();
  }

  @Override
  public CompletionStage<Boolean> checkSchemaAgreementAsync() {
    return originalTracingCqlSession.checkSchemaAgreementAsync();
  }

  @Override
  public boolean checkSchemaAgreement() {
    return originalTracingCqlSession.checkSchemaAgreement();
  }

  @Override
  public DriverContext getContext() {
    return originalTracingCqlSession.getContext();
  }

  @Override
  public Optional<CqlIdentifier> getKeyspace() {
    return originalTracingCqlSession.getKeyspace();
  }

  @Override
  public Optional<Metrics> getMetrics() {
    return originalTracingCqlSession.getMetrics();
  }

  @Override
  public CompletionStage<Void> closeFuture() {
    return originalTracingCqlSession.closeFuture();
  }

  @Override
  public boolean isClosed() {
    return originalTracingCqlSession.isClosed();
  }

  @Override
  public CompletionStage<Void> closeAsync() {
    return originalTracingCqlSession.closeAsync();
  }

  @Override
  public CompletionStage<Void> forceCloseAsync() {
    return originalTracingCqlSession.forceCloseAsync();
  }

  @Override
  public void close() {
    originalTracingCqlSession.close();
  }

  @Override
  @Nullable
  public <REQUEST extends Request, RESULT> RESULT execute(
      REQUEST request, GenericType<RESULT> resultType) {
    return originalTracingCqlSession.execute(request, resultType);
  }

  @Override
  public ResultSet execute(String query) {
    return originalTracingCqlSession.execute(query);
  }

  @Override
  public ResultSet execute(Statement<?> statement) {
    return originalTracingCqlSession.execute(statement);
  }

  @Override
  public CompletionStage<AsyncResultSet> executeAsync(Statement<?> statement) {
    return originalTracingCqlSession.executeAsync(statement);
  }

  @Override
  public CompletionStage<AsyncResultSet> executeAsync(String query) {
    return originalTracingCqlSession.executeAsync(query);
  }

  @Override
  public ReactiveResultSet executeReactive(Statement<?> statement) {
    return new DefaultReactiveResultSet(() -> originalTracingCqlSession.executeAsync(statement));
  }
}
