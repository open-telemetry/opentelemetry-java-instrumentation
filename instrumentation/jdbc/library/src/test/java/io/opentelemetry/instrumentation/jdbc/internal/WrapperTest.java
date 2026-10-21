/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createStatementInstrumenter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory.createTransactionInstrumenter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

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

  void validate(Class<?> jdbcClass, Class<?> wrapperClass) throws NoSuchMethodException {
    for (Method method : jdbcClass.getMethods()) {
      Method result = wrapperClass.getMethod(method.getName(), method.getParameterTypes());
      if (!result.getDeclaringClass().getName().startsWith("io.opentelemetry")) {
        fail(wrapperClass.getName() + " does not override " + method);
      }
    }
  }
}
