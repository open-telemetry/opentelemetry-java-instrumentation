/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v4_0.VertxSqlClientSingletons.currentClientInfoReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VertxSqlClientScopedStateTest {

  @AfterEach
  void cleanup() {
    currentClientInfoReference().restore(null);
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
