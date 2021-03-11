/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JdbcMaps {
  public static final Map<Connection, DbInfo> connectionInfo = Cache.<Connection, DbInfo>newBuilder().setWeakKeys().build().asMap();
  public static final Map<PreparedStatement, String> preparedStatements = Cache.<PreparedStatement, String>newBuilder().setWeakKeys().build().asMap();
}
