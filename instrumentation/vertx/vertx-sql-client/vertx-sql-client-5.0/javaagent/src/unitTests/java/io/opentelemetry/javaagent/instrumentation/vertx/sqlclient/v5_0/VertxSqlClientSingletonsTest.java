/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentClientInfo;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentConstructionState;
import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentQuerySupplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.vertx.core.Future;
import io.vertx.sqlclient.PreparedStatement;
import io.vertx.sqlclient.internal.Connection;
import io.vertx.sqlclient.internal.SqlConnectionInternal;
import org.junit.jupiter.api.Test;

class VertxSqlClientSingletonsTest {
  private static boolean initialized;

  @Test
  void findsAndCachesWrappedConnectionInfo() {
    Connection connection = mock(Connection.class);
    Connection wrapper = mock(Connection.class);
    when(wrapper.unwrap()).thenReturn(connection);
    VertxSqlClientInfo info = VertxSqlClientInfo.createUnknown("test");
    VirtualField.find(Connection.class, VertxSqlClientInfo.class).set(connection, info);

    assertThat(VertxSqlClientSingletons.getConnectionInfo(wrapper)).isSameAs(info);
    assertThat(VertxSqlClientSingletons.getConnectionInfo(wrapper)).isSameAs(info);
    verify(wrapper).unwrap();
  }

  @Test
  void findsAndCachesSqlConnectionInfo() {
    Connection connection = mock(Connection.class);
    SqlConnectionInternal wrapper = mock(SqlConnectionInternal.class);
    when(wrapper.unwrap()).thenReturn(connection);
    VertxSqlClientInfo info = VertxSqlClientInfo.createUnknown("test");
    VirtualField.find(Connection.class, VertxSqlClientInfo.class).set(connection, info);

    assertThat(VertxSqlClientSingletons.getConnectionInfo(wrapper)).isSameAs(info);
    assertThat(VertxSqlClientSingletons.getConnectionInfo(wrapper)).isSameAs(info);
    verify(wrapper).unwrap();
  }

  @Test
  void stopsUnwrappingConnectionReturningItself() {
    Connection connection = mock(Connection.class);
    when(connection.unwrap()).thenReturn(connection);

    assertThat(VertxSqlClientSingletons.getConnectionInfo(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresUnwrapFailure() {
    Connection connection = mock(Connection.class);
    when(connection.unwrap()).thenThrow(new IllegalStateException("unwrap failed"));

    assertThat(VertxSqlClientSingletons.getConnectionInfo(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresSqlConnectionUnwrapFailure() {
    SqlConnectionInternal connection = mock(SqlConnectionInternal.class);
    when(connection.unwrap()).thenThrow(new IllegalStateException("unwrap failed"));

    assertThat(VertxSqlClientSingletons.getConnectionInfo(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresMissingConnectionInfo() {
    assertThat(VertxSqlClientSingletons.getConnectionInfo(mock(Connection.class))).isNull();
    assertThat(VertxSqlClientSingletons.getConnectionInfo(mock(SqlConnectionInternal.class)))
        .isNull();
    assertThat(VertxSqlClientSingletons.getConnectionInfo(new Object())).isNull();
  }

  @Test
  void loadsVersionedClassWithoutInitializingIt() {
    assertThat(initialized).isFalse();

    Class<?> loadedClass =
        VertxSqlClientSingletons.loadVersionedClass(
            FirstCarrier.class.getName(), "missing.SecondCarrier");

    assertThat(loadedClass).isSameAs(FirstCarrier.class);
    assertThat(initialized).isFalse();

    loadedClass =
        VertxSqlClientSingletons.loadVersionedClass(
            "missing.FirstCarrier", SecondCarrier.class.getName());

    assertThat(loadedClass).isSameAs(SecondCarrier.class);
    assertThat(initialized).isFalse();
  }

  @Test
  void restoresNestedPreparedStatementState() {
    VertxSqlClientInfo ambientInfo = VertxSqlClientInfo.createUnknown("ambient");
    VertxSqlClientInfo firstInfo = VertxSqlClientInfo.createUnknown("first");
    VertxSqlClientInfo secondInfo = VertxSqlClientInfo.createUnknown("second");
    VertxSqlClientSupplierInfo ambientSupplier = new VertxSqlClientSupplierInfo(ambientInfo);
    PreparedStatement firstStatement = mock(PreparedStatement.class);
    PreparedStatement secondStatement = mock(PreparedStatement.class);
    VertxSqlClientSingletons.attachPreparedStatementInfo(
        Future.succeededFuture(firstStatement), firstInfo);
    VertxSqlClientSingletons.attachPreparedStatementInfo(
        Future.succeededFuture(secondStatement), secondInfo);

    VertxSqlClientInfo initialInfo = currentClientInfo().set(ambientInfo);
    VertxSqlClientSupplierInfo initialSupplier = currentQuerySupplier().set(ambientSupplier);
    try {
      PreparedStatementInstrumentation.QueryAdvice.QueryAdviceState firstState =
          PreparedStatementInstrumentation.QueryAdvice.onEnter(firstStatement);
      assertThat(currentClientInfo().get()).isSameAs(firstInfo);
      assertThat(currentQuerySupplier().get()).isNull();

      PreparedStatementInstrumentation.QueryAdvice.QueryAdviceState secondState =
          PreparedStatementInstrumentation.QueryAdvice.onEnter(secondStatement);
      assertThat(currentClientInfo().get()).isSameAs(secondInfo);
      assertThat(currentQuerySupplier().get()).isNull();

      PreparedStatementInstrumentation.QueryAdvice.onExit(secondState);
      assertThat(currentClientInfo().get()).isSameAs(firstInfo);
      assertThat(currentQuerySupplier().get()).isNull();

      PreparedStatementInstrumentation.QueryAdvice.onExit(firstState);
      assertThat(currentClientInfo().get()).isSameAs(ambientInfo);
      assertThat(currentQuerySupplier().get()).isSameAs(ambientSupplier);
    } finally {
      currentQuerySupplier().restore(initialSupplier);
      currentClientInfo().restore(initialInfo);
    }
  }

  @Test
  void restoresConstructionStateWhenCompletionThrows() {
    RuntimeException failure = new RuntimeException("completion failed");
    VertxSqlClientConstructionState ambientState =
        new VertxSqlClientConstructionState(null, "ambient");
    VertxSqlClientConstructionState failingState = mock(VertxSqlClientConstructionState.class);
    doThrow(failure).when(failingState).complete(null);

    VertxSqlClientConstructionState initialState = currentConstructionState().set(ambientState);
    VertxSqlClientConstructionState previousState = currentConstructionState().set(failingState);
    Object[] enterState = {
      null,
      new ClientBuilderInstrumentation.BuildAdvice.BuildState(failingState, null, previousState)
    };
    try {
      assertThatThrownBy(
              () -> ClientBuilderInstrumentation.BuildAdvice.onExit(null, null, enterState))
          .isSameAs(failure);
      assertThat(currentConstructionState().get()).isSameAs(ambientState);
    } finally {
      currentConstructionState().restore(initialState);
    }
  }

  private static class FirstCarrier {
    static {
      initialized = true;
    }
  }

  private static class SecondCarrier {
    static {
      initialized = true;
    }
  }
}
