/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jdbc;

import static io.opentelemetry.javaagent.instrumentation.api.WeakMap.Provider.newWeakMap;

import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import io.opentelemetry.javaagent.instrumentation.api.jdbc.DBInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JDBCMaps {
  public static final WeakMap<Connection, DBInfo> connectionInfo = newWeakMap();
  public static final WeakMap<PreparedStatement, String> preparedStatements = newWeakMap();
}
