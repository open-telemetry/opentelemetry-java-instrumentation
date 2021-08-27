/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcData;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.function.Function;

/**
 * Provides means to associate extra info with JDBC {@link Connection} and {@link PreparedStatement}
 * using {@link ContextStore} which is more efficient than using a weak map.
 */
public class AgentDataStoreFactory implements JdbcData.DataStoreFactory {
  private final JdbcData.DataStore<Connection, DbInfo> connectionDataStore;
  private final JdbcData.DataStore<PreparedStatement, String> preparedStatementsDataStore;

  public AgentDataStoreFactory() {
    ContextStore<Connection, DbInfo> connectionContextStore =
        InstrumentationContext.get(Connection.class, DbInfo.class);
    connectionDataStore = new ContextStoreDataStore<>(connectionContextStore);
    ContextStore<PreparedStatement, String> preparedStatementsContextStore =
        InstrumentationContext.get(PreparedStatement.class, String.class);
    preparedStatementsDataStore = new ContextStoreDataStore<>(preparedStatementsContextStore);
  }

  @Override
  public JdbcData.DataStore<Connection, DbInfo> connectionInfoDataStore() {
    return connectionDataStore;
  }

  @Override
  public JdbcData.DataStore<PreparedStatement, String> preparedStatementsDataStore() {
    return preparedStatementsDataStore;
  }

  private static class ContextStoreDataStore<K, V> implements JdbcData.DataStore<K, V> {
    private final ContextStore<K, V> contextStore;

    public ContextStoreDataStore(ContextStore<K, V> contextStore) {
      this.contextStore = contextStore;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
      return contextStore.putIfAbsent(key, () -> mappingFunction.apply(key));
    }

    @Override
    public V get(K key) {
      return contextStore.get(key);
    }

    @Override
    public void put(K key, V value) {
      contextStore.put(key, value);
    }
  }
}
