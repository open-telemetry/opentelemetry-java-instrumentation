/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0.VertxSqlClientSingletons.currentClientInfoReference;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.SqlConnectOptions;
import io.vertx.sqlclient.impl.SqlClientBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VertxSqlClientScopedStateTest {

  @AfterEach
  void cleanup() {
    currentClientInfoReference().restore(null);
  }

  @Test
  void poolFactoriesRestoreAmbientReferenceAfterFailedConstruction() {
    VertxSqlClientInfoReference outer = new VertxSqlClientInfoReference(null);
    currentClientInfoReference().set(outer);
    SqlConnectOptions options = new SqlConnectOptions();
    PoolInstrumentation.PoolConstructionState state =
        PoolInstrumentation.PoolAdvice.onEnter(options, "io.vertx.pgclient.PgPool");
    try {
      assertThat(currentClientInfoReference().get()).isSameAs(state.getInfoReference());
      PoolInstrumentation.PoolConstructionState nested =
          PoolInstrumentation.ServerListAdvice.onEnter(
              singletonList(options), "io.vertx.pgclient.PgPool");
      try {
        assertThat(currentClientInfoReference().get()).isSameAs(state.getInfoReference());
      } finally {
        PoolInstrumentation.ServerListAdvice.onExit(null, singletonList(options), nested);
      }
      assertThat(currentClientInfoReference().get()).isSameAs(state.getInfoReference());
    } finally {
      // A throwing factory has no returned pool to attach metadata to.
      PoolInstrumentation.PoolAdvice.onExit(null, options, state);
    }
    assertThat(currentClientInfoReference().get()).isSameAs(outer);

    state =
        PoolInstrumentation.ServerListAdvice.onEnter(
            singletonList(options), "io.vertx.pgclient.PgPool");
    PoolInstrumentation.ServerListAdvice.onExit(null, singletonList(options), state);
    assertThat(currentClientInfoReference().get()).isSameAs(outer);
  }

  @Test
  void nestedClientQueriesRestoreAmbientReference() {
    VertxSqlClientInfoReference outer = new VertxSqlClientInfoReference(null);
    VertxSqlClientInfoReference clientReference = new VertxSqlClientInfoReference(null);
    SqlClientBase<?> client = mock(SqlClientBase.class);
    VertxSqlClientSingletons.attachClientInfoReference(client, clientReference);
    currentClientInfoReference().set(outer);
    VertxSqlClientInfoReference previous = SqlClientBaseInstrumentation.QueryAdvice.onEnter(client);
    try {
      assertThat(currentClientInfoReference().get()).isSameAs(clientReference);
      VertxSqlClientInfoReference nested = SqlClientBaseInstrumentation.QueryAdvice.onEnter(client);
      try {
        assertThat(currentClientInfoReference().get()).isSameAs(clientReference);
      } finally {
        SqlClientBaseInstrumentation.QueryAdvice.onExit(nested);
      }
      assertThat(currentClientInfoReference().get()).isSameAs(clientReference);
    } finally {
      SqlClientBaseInstrumentation.QueryAdvice.onExit(previous);
    }
    assertThat(currentClientInfoReference().get()).isSameAs(outer);
  }

  @Test
  void nestedPreparedStatementsRestoreTheirReferences() {
    VertxSqlClientInfoReference outer = new VertxSqlClientInfoReference(null);
    currentClientInfoReference().set(outer);
    PreparedStatement statement = mock(PreparedStatement.class);
    VertxSqlClientInfoReference statementReference = new VertxSqlClientInfoReference(null);
    assertThat(
            VertxSqlClientSingletons.attachPreparedStatementInfoReference(
                    Future.succeededFuture(statement), statementReference)
                .result())
        .isSameAs(statement);
    VertxSqlClientInfoReference previous =
        PreparedStatementInstrumentation.QueryAdvice.onEnter(statement);
    assertThat(currentClientInfoReference().get()).isSameAs(statementReference);
    VertxSqlClientInfoReference nested =
        PreparedStatementInstrumentation.QueryAdvice.onEnter(mock(PreparedStatement.class));
    assertThat(currentClientInfoReference().get()).isNull();
    PreparedStatementInstrumentation.QueryAdvice.onExit(nested);
    assertThat(currentClientInfoReference().get()).isSameAs(statementReference);
    PreparedStatementInstrumentation.QueryAdvice.onExit(previous);
    assertThat(currentClientInfoReference().get()).isSameAs(outer);
  }
}
