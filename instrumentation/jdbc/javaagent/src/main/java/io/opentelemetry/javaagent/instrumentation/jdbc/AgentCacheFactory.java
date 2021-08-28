/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Provides means to associate extra info with JDBC {@link Connection} and {@link PreparedStatement}
 * using {@link ContextStore} which is more efficient than using a weak map.
 */
public class AgentCacheFactory implements JdbcData.CacheFactory {
  private final Cache<Connection, DbInfo> connectionCache;
  private final Cache<PreparedStatement, String> preparedStatementCache;

  public AgentCacheFactory() {
    ContextStore<Connection, DbInfo> connectionContextStore =
        InstrumentationContext.get(Connection.class, DbInfo.class);
    connectionCache = connectionContextStore.asCache();
    ContextStore<PreparedStatement, String> preparedStatementContextStore =
        InstrumentationContext.get(PreparedStatement.class, String.class);
    preparedStatementCache = preparedStatementContextStore.asCache();
  }

  @Override
  public Cache<Connection, DbInfo> connectionInfoCache() {
    return connectionCache;
  }

  @Override
  public Cache<PreparedStatement, String> preparedStatementCache() {
    return preparedStatementCache;
  }
}
