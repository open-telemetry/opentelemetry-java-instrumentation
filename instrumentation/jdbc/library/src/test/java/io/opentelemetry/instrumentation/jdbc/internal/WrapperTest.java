/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection.OpenTelemetryConnectionJdbc43;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WrapperTest {

  @Test
  void wrapperImplementsAllMethods() throws Exception {
    validate(Statement.class, OpenTelemetryStatement.class);
    validate(PreparedStatement.class, OpenTelemetryPreparedStatement.class);
    validate(CallableStatement.class, OpenTelemetryCallableStatement.class);
    validate(
        Connection.class,
        OpenTelemetryConnection.hasJdbc43()
            ? OpenTelemetryConnectionJdbc43.class
            : OpenTelemetryConnection.class);
    validate(ResultSet.class, OpenTelemetryResultSet.class);
  }

  void validate(Class<?> jdbcClass, Class<?> wrapperClass) throws Exception {
    for (Method method : jdbcClass.getMethods()) {
      Method result = wrapperClass.getMethod(method.getName(), method.getParameterTypes());
      if (!result.getDeclaringClass().getName().startsWith("io.opentelemetry")) {
        Assertions.fail(wrapperClass.getName() + " does not override " + method);
      }
    }
  }
}
