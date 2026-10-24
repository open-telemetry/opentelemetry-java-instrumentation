/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createTransactionInstrumenter;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.TestConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenTelemetryConnectionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final List<String> executedSql = new ArrayList<>();

  @BeforeEach
  void resetTest() {
    executedSql.clear();
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyCreateStatementReturnsOtelWrapper() throws SQLException {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);
    cleanup.deferCleanup(connection);

    assertThat(connection.createStatement()).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(connection.createStatement(0, 0, 0)).isInstanceOf(OpenTelemetryStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.createStatement()).instrumenter)
        .isEqualTo(connection.statementInstrumenter);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareStatementReturnsOtelWrapper() throws SQLException {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);
    cleanup.deferCleanup(connection);

    String query = "SELECT * FROM users";

    assertThat(connection.prepareStatement(query))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new int[] {0}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, new String[] {"id"}))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(connection.prepareStatement(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryPreparedStatement.class);
    assertThat(
            ((OpenTelemetryStatement<Statement>) connection.prepareStatement(query)).instrumenter)
        .isEqualTo(connection.statementInstrumenter);
  }

  @SuppressWarnings("unchecked")
  @Test
  void testVerifyPrepareCallReturnsOtelWrapper() throws SQLException {
    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryConnection connection = getConnection(openTelemetry);
    cleanup.deferCleanup(connection);

    String query = "SELECT * FROM users";

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);

    assertThat(connection.prepareCall(query)).isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(connection.prepareCall(query, 0, 0, 0))
        .isInstanceOf(OpenTelemetryCallableStatement.class);
    assertThat(((OpenTelemetryStatement<Statement>) connection.prepareCall(query)).instrumenter)
        .isEqualTo(connection.statementInstrumenter);
  }

  @ParameterizedTest
  @MethodSource("wrappers")
  void unwrapReturnsWrapperForSupportedInterfaces(Wrapper wrapper, Wrapper delegate)
      throws SQLException {
    for (Class<?> iface : supportedInterfaces(wrapper)) {
      assertThat(wrapper.unwrap(iface)).isSameAs(wrapper);
    }
    verifyNoInteractions(delegate);
  }

  @ParameterizedTest
  @MethodSource("wrappers")
  void isWrapperForDoesNotConsultDelegateForSupportedInterfaces(Wrapper wrapper, Wrapper delegate)
      throws SQLException {
    for (Class<?> iface : supportedInterfaces(wrapper)) {
      assertThat(wrapper.isWrapperFor(iface)).isTrue();
    }
    verifyNoInteractions(delegate);
  }

  @ParameterizedTest
  @MethodSource("wrappers")
  void delegatesVendorInterfaces(Wrapper wrapper, Wrapper delegate) throws SQLException {
    VendorJdbcObject vendorObject = mock(VendorJdbcObject.class);
    when(delegate.unwrap(VendorJdbcObject.class)).thenReturn(vendorObject);
    when(delegate.isWrapperFor(VendorJdbcObject.class)).thenReturn(true);

    assertThat(wrapper.unwrap(VendorJdbcObject.class)).isSameAs(vendorObject);
    assertThat(wrapper.isWrapperFor(VendorJdbcObject.class)).isTrue();
    verify(delegate).unwrap(VendorJdbcObject.class);
    verify(delegate).isWrapperFor(VendorJdbcObject.class);
  }

  @ParameterizedTest
  @MethodSource("wrappers")
  void preservesUnsupportedInterfaceBehavior(Wrapper wrapper, Wrapper delegate)
      throws SQLException {
    SQLException exception = new SQLException("Unsupported interface");
    when(delegate.unwrap(Runnable.class)).thenThrow(exception);
    when(delegate.isWrapperFor(Runnable.class)).thenReturn(false);

    assertThatThrownBy(() -> wrapper.unwrap(Runnable.class)).isSameAs(exception);
    assertThat(wrapper.isWrapperFor(Runnable.class)).isFalse();
    verify(delegate).unwrap(Runnable.class);
    verify(delegate).isWrapperFor(Runnable.class);

    when(delegate.isWrapperFor(Runnable.class)).thenThrow(exception);
    assertThatThrownBy(() -> wrapper.isWrapperFor(Runnable.class)).isSameAs(exception);
  }

  private static List<Class<?>> supportedInterfaces(Wrapper wrapper) {
    return Stream.of(
            Connection.class,
            Statement.class,
            PreparedStatement.class,
            CallableStatement.class,
            ResultSet.class,
            Wrapper.class,
            AutoCloseable.class)
        .filter(iface -> iface.isInstance(wrapper))
        .collect(toList());
  }

  private static Stream<Arguments> wrappers() {
    Connection connectionDelegate = mock(Connection.class);
    OpenTelemetryConnection connection = getConnection(OpenTelemetry.noop(), connectionDelegate);
    Connection versionedConnectionDelegate = mock(Connection.class);
    Connection versionedConnection =
        OpenTelemetryConnection.create(
            versionedConnectionDelegate,
            getDbInfo(),
            connection.statementInstrumenter,
            connection.transactionInstrumenter,
            false,
            SqlCommenter.noop());
    Statement statementDelegate = mock(Statement.class);
    PreparedStatement preparedDelegate = mock(PreparedStatement.class);
    CallableStatement callableDelegate = mock(CallableStatement.class);
    ResultSet resultSetDelegate = mock(ResultSet.class);
    Statement statement =
        new OpenTelemetryStatement<>(
            statementDelegate,
            connection,
            getDbInfo(),
            connection.statementInstrumenter,
            SqlCommenter.noop());

    return Stream.of(
        Arguments.of(connection, connectionDelegate),
        Arguments.of(versionedConnection, versionedConnectionDelegate),
        Arguments.of(statement, statementDelegate),
        Arguments.of(
            new OpenTelemetryPreparedStatement<>(
                preparedDelegate,
                connection,
                getDbInfo(),
                "SELECT 1",
                connection.statementInstrumenter,
                false,
                SqlCommenter.noop()),
            preparedDelegate),
        Arguments.of(
            new OpenTelemetryCallableStatement<>(
                callableDelegate,
                connection,
                getDbInfo(),
                "CALL test()",
                connection.statementInstrumenter,
                false,
                SqlCommenter.noop()),
            callableDelegate),
        Arguments.of(OpenTelemetryResultSet.wrap(resultSetDelegate, statement), resultSetDelegate));
  }

  private static DbInfo getDbInfo() {
    return DbInfo.builder().dbSystemName("my_system").dbNamespace("my_name").build();
  }

  private static OpenTelemetryConnection getConnection(OpenTelemetry openTelemetry) {
    return getConnection(openTelemetry, new TestConnection(executedSql::add));
  }

  private static OpenTelemetryConnection getConnection(
      OpenTelemetry openTelemetry, Connection delegate) {
    Instrumenter<DbRequest, Void> statementInstrumenter =
        createStatementInstrumenter(
            openTelemetry,
            /* enabled= */ true,
            /* querySanitizationEnabled= */ true,
            /* captureQueryParameters= */ false);
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        createTransactionInstrumenter(openTelemetry, true);
    DbInfo dbInfo = getDbInfo();
    return new OpenTelemetryConnection(
        delegate,
        dbInfo,
        statementInstrumenter,
        transactionInstrumenter,
        false,
        SqlCommenter.noop());
  }

  private interface VendorJdbcObject {}
}
