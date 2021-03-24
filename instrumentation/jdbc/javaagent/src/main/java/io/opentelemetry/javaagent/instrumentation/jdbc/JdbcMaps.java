/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JdbcMaps {
  public static final Cache<Connection, DbInfo> connectionInfo =
      Cache.newBuilder().setWeakKeys().build();
  public static final Cache<PreparedStatement, String> preparedStatements =
      Cache.newBuilder().setWeakKeys().build();
}
