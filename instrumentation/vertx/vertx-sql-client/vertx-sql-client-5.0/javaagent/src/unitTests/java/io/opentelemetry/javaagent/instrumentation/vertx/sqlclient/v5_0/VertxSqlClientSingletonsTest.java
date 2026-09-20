/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientSingletons.currentConstructionState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0.VertxSqlClientInfo;
import io.vertx.core.Future;
import io.vertx.sqlclient.internal.Connection;
import io.vertx.sqlclient.internal.SqlClientBase;
import io.vertx.sqlclient.internal.SqlConnectionBase;
import io.vertx.sqlclient.internal.SqlConnectionInternal;
import org.junit.jupiter.api.Test;

class VertxSqlClientSingletonsTest {
  private static boolean initialized;

  @Test
  void findsAndCachesWrappedConnectionState() {
    Connection connection = mock(Connection.class);
    Connection wrapper = mock(Connection.class);
    when(wrapper.unwrap()).thenReturn(connection);
    VertxSqlClientState state =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("test"), false);
    VirtualField.find(Connection.class, VertxSqlClientState.class).set(connection, state);

    assertThat(VertxSqlClientSingletons.getClientState(wrapper)).isSameAs(state);
    assertThat(VertxSqlClientSingletons.getClientState(wrapper)).isSameAs(state);
    verify(wrapper).unwrap();
  }

  @Test
  void findsAndCachesSqlConnectionState() {
    Connection connection = mock(Connection.class);
    SqlConnectionBase<?> wrapper = mock(SqlConnectionBase.class);
    when(wrapper.unwrap()).thenReturn(connection);
    VertxSqlClientState state =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("test"), false);
    VirtualField.find(Connection.class, VertxSqlClientState.class).set(connection, state);

    assertThat(VertxSqlClientSingletons.getClientState(wrapper)).isSameAs(state);
    assertThat(VertxSqlClientSingletons.getClientState(wrapper)).isSameAs(state);
    verify(wrapper).unwrap();
  }

  @Test
  void stopsUnwrappingConnectionReturningItself() {
    Connection connection = mock(Connection.class);
    when(connection.unwrap()).thenReturn(connection);

    assertThat(VertxSqlClientSingletons.getClientState(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresUnwrapFailure() {
    Connection connection = mock(Connection.class);
    when(connection.unwrap()).thenThrow(new IllegalStateException("unwrap failed"));

    assertThat(VertxSqlClientSingletons.getClientState(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresSqlConnectionUnwrapFailure() {
    SqlConnectionInternal connection = mock(SqlConnectionInternal.class);
    when(connection.unwrap()).thenThrow(new IllegalStateException("unwrap failed"));

    assertThat(VertxSqlClientSingletons.getClientState(connection)).isNull();
    verify(connection).unwrap();
  }

  @Test
  void ignoresMissingClientState() {
    assertThat(VertxSqlClientSingletons.getClientState(mock(Connection.class))).isNull();
    assertThat(VertxSqlClientSingletons.getClientState(mock(SqlConnectionInternal.class))).isNull();
    assertThat(VertxSqlClientSingletons.getClientState(new Object())).isNull();
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
  void keepsClientMetadataAndSupplierModeTogether() {
    SqlClientBase first = mock(SqlClientBase.class);
    SqlClientBase second = mock(SqlClientBase.class);
    VertxSqlClientState supplier =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("first"), true);
    VertxSqlClientState fixed =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("second"), false);
    VertxSqlClientSingletons.attachClientState(first, supplier);
    VertxSqlClientSingletons.attachClientState(second, fixed);

    assertThat(VertxSqlClientSingletons.getClientState(first)).isSameAs(supplier);
    assertThat(VertxSqlClientSingletons.getClientState(second)).isSameAs(fixed);
    VertxSqlClientSingletons.attachClientState(first, fixed);
    assertThat(VertxSqlClientSingletons.getClientState(first)).isSameAs(fixed);
    VertxSqlClientSingletons.attachClientState(first, null);
    assertThat(VertxSqlClientSingletons.getClientState(first)).isNull();
  }

  @Test
  void publishesFixedConfigurationToThePreparedQueryConnection() {
    SqlConnectionBase<?> client = mock(SqlConnectionBase.class);
    Connection connection = mock(Connection.class);
    when(client.unwrap()).thenReturn(connection);
    VertxSqlClientState selected =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("selected"), false);
    VertxSqlClientState configured =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("configured"), false);
    VirtualField.find(Connection.class, VertxSqlClientState.class).set(connection, selected);

    assertThat(
            VertxSqlClientSingletons.attachClientState(Future.succeededFuture(client), configured)
                .result())
        .isSameAs(client);
    assertThat(VertxSqlClientSingletons.getClientState(client)).isSameAs(configured);
    assertThat(VertxSqlClientSingletons.getClientState(connection)).isSameAs(configured);
  }

  @Test
  void resolvesSupplierStateFromTheAcquiredConnection() {
    SqlConnectionBase<?> client = mock(SqlConnectionBase.class);
    Connection connection = mock(Connection.class);
    when(client.unwrap()).thenReturn(connection);
    VertxSqlClientState supplied =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("supplied"), false);
    VertxSqlClientState unresolved =
        new VertxSqlClientState(VertxSqlClientInfo.createUnknown("unresolved"), true);
    VirtualField.find(Connection.class, VertxSqlClientState.class).set(connection, supplied);

    assertThat(
            VertxSqlClientSingletons.attachClientState(Future.succeededFuture(client), unresolved)
                .result())
        .isSameAs(client);
    assertThat(VertxSqlClientSingletons.getClientState(client)).isSameAs(supplied);
    assertThat(VertxSqlClientSingletons.getClientState(connection)).isSameAs(supplied);
  }

  @Test
  void acquiredConnectionDoesNotKeepThePoolSupplierMarker() {
    SqlConnectionBase<?> client = mock(SqlConnectionBase.class);
    VertxSqlClientInfo info = VertxSqlClientInfo.createUnknown("postgresql");
    VertxSqlClientState unresolved = new VertxSqlClientState(info, true);

    VertxSqlClientSingletons.attachClientState(Future.succeededFuture(client), unresolved);

    VertxSqlClientState state = VertxSqlClientSingletons.getClientState(client);
    assertThat(state.getInfo()).isSameAs(info);
    assertThat(state.isSupplier()).isFalse();
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
