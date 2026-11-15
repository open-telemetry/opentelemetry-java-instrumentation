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
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenter;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection.OpenTelemetryConnectionJdbc43;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection.OpenTelemetryConnectionJdbc45;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WrapperTest {

  @Test
  void wrapperImplementsAllMethods() throws NoSuchMethodException {
    validate(Statement.class, OpenTelemetryStatement.class);
    validate(PreparedStatement.class, OpenTelemetryPreparedStatement.class);
    validate(CallableStatement.class, OpenTelemetryCallableStatement.class);
    validate(Connection.class, connectionWrapperClass());
    validate(ResultSet.class, OpenTelemetryResultSet.class);
  }

  private static Class<?> connectionWrapperClass() {
    if (OpenTelemetryConnection.hasJdbc45()) {
      return OpenTelemetryConnectionJdbc45.class;
    }
    if (OpenTelemetryConnection.hasJdbc43()) {
      return OpenTelemetryConnectionJdbc43.class;
    }
    return OpenTelemetryConnection.class;
  }

  @Test
  void jdbc45MethodsDelegate() throws Exception {
    assumeTrue(OpenTelemetryConnection.hasJdbc45());

    List<String> calls = new ArrayList<>();
    Connection delegate =
        (Connection)
            Proxy.newProxyInstance(
                WrapperTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  calls.add(method.getName());
                  switch (method.getName()) {
                    case "enquoteLiteral":
                      return "'" + args[0] + "'";
                    case "enquoteIdentifier":
                      return "\"" + args[0] + "\"";
                    case "enquoteNCharLiteral":
                      return "N'" + args[0] + "'";
                    case "isSimpleIdentifier":
                      return true;
                    default:
                      throw new AssertionError("unexpected call: " + method.getName());
                  }
                });
    Connection wrapper = wrap(delegate);

    assertThat(invokeConnectionMethod(wrapper, "enquoteLiteral", "val")).isEqualTo("'val'");
    assertThat(
            Connection.class
                .getMethod("enquoteIdentifier", String.class, boolean.class)
                .invoke(wrapper, "id", true))
        .isEqualTo("\"id\"");
    assertThat(invokeConnectionMethod(wrapper, "enquoteNCharLiteral", "nval")).isEqualTo("N'nval'");
    assertThat(invokeConnectionMethod(wrapper, "isSimpleIdentifier", "simple")).isEqualTo(true);
    assertThat(calls)
        .containsExactly(
            "enquoteLiteral", "enquoteIdentifier", "enquoteNCharLiteral", "isSimpleIdentifier");
  }

  @Test
  void jdbc45MethodsPropagateSqlException() throws Exception {
    assumeTrue(OpenTelemetryConnection.hasJdbc45());

    SQLException expected = new SQLException("boom");
    Connection delegate =
        (Connection)
            Proxy.newProxyInstance(
                WrapperTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                  throw expected;
                });
    Connection wrapper = wrap(delegate);

    assertThatThrownBy(() -> invokeConnectionMethod(wrapper, "enquoteLiteral", "val"))
        .isInstanceOf(InvocationTargetException.class)
        .cause()
        .isSameAs(expected);
  }

  @ParameterizedTest
  @MethodSource("wrappers")
  void supportedInterfacesReturnWrapperWithoutConsultingDelegate(Wrapper wrapper, Wrapper delegate)
      throws SQLException {
    for (Class<?> iface : supportedInterfaces(wrapper)) {
      assertThat(wrapper.unwrap(iface)).isSameAs(wrapper);
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
    OpenTelemetryConnection connection = (OpenTelemetryConnection) wrap(connectionDelegate);
    Statement statementDelegate = mock(Statement.class);
    PreparedStatement preparedDelegate = mock(PreparedStatement.class);
    CallableStatement callableDelegate = mock(CallableStatement.class);
    ResultSet resultSetDelegate = mock(ResultSet.class);
    DbInfo dbInfo = DbInfo.builder().build();
    Statement statement =
        new OpenTelemetryStatement<>(
            statementDelegate,
            connection,
            dbInfo,
            connection.statementInstrumenter,
            SqlCommenter.noop());

    return Stream.of(
        Arguments.of(connection, connectionDelegate),
        Arguments.of(statement, statementDelegate),
        Arguments.of(
            new OpenTelemetryPreparedStatement<>(
                preparedDelegate,
                connection,
                dbInfo,
                "SELECT 1",
                connection.statementInstrumenter,
                false,
                SqlCommenter.noop()),
            preparedDelegate),
        Arguments.of(
            new OpenTelemetryCallableStatement<>(
                callableDelegate,
                connection,
                dbInfo,
                "CALL test()",
                connection.statementInstrumenter,
                false,
                SqlCommenter.noop()),
            callableDelegate),
        Arguments.of(OpenTelemetryResultSet.wrap(resultSetDelegate, statement), resultSetDelegate));
  }

  private static Connection wrap(Connection delegate) {
    OpenTelemetry noop = OpenTelemetry.noop();
    return OpenTelemetryConnection.create(
        delegate,
        DbInfo.builder().build(),
        createStatementInstrumenter(
            noop,
            /* enabled= */ true,
            /* querySanitizationEnabled= */ true,
            /* captureQueryParameters= */ false),
        createTransactionInstrumenter(noop, /* enabled= */ true),
        /* captureQueryParameters= */ false,
        SqlCommenter.noop());
  }

  private static Object invokeConnectionMethod(Connection wrapper, String name, String arg)
      throws Exception {
    return Connection.class.getMethod(name, String.class).invoke(wrapper, arg);
  }

  private interface VendorJdbcObject {}

  void validate(Class<?> jdbcClass, Class<?> wrapperClass) throws NoSuchMethodException {
    for (Method method : jdbcClass.getMethods()) {
      Method result = wrapperClass.getMethod(method.getName(), method.getParameterTypes());
      if (!result.getDeclaringClass().getName().startsWith("io.opentelemetry")) {
        fail(wrapperClass.getName() + " does not override " + method);
      }
    }
  }
}
