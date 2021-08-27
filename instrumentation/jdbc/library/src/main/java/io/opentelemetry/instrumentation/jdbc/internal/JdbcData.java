/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Holds info associated with JDBC connections and prepared statements. */
public class JdbcData {
  private static final Logger logger = LoggerFactory.getLogger(JdbcData.class);

  private static final DataStoreFactory dataStoreFactory = getDataStoreFactor();

  public static DataStore<Connection, DbInfo> connectionInfo =
      dataStoreFactory.connectionInfoDataStore();
  public static DataStore<PreparedStatement, String> preparedStatements =
      dataStoreFactory.preparedStatementsDataStore();

  private static DataStoreFactory getDataStoreFactor() {
    try {
      // this class is provided by jdbc javaagent instrumentation
      Class<?> clazz =
          Class.forName("io.opentelemetry.javaagent.instrumentation.jdbc.AgentDataStoreFactory");
      return (DataStoreFactory) clazz.getConstructor().newInstance();
    } catch (ClassNotFoundException ignored) {
      // ignored, this is expected when running as library instrumentation
    } catch (Exception exception) {
      logger.error("Failed to instantiate AgentDataStoreFactory", exception);
    }

    return new DefaultDataStoreFactory();
  }

  public interface DataStoreFactory {
    DataStore<Connection, DbInfo> connectionInfoDataStore();

    DataStore<PreparedStatement, String> preparedStatementsDataStore();
  }

  private static class DefaultDataStoreFactory implements DataStoreFactory {
    @Override
    public DataStore<Connection, DbInfo> connectionInfoDataStore() {
      return new WeakMapDataStore<>();
    }

    @Override
    public DataStore<PreparedStatement, String> preparedStatementsDataStore() {
      return new WeakMapDataStore<>();
    }
  }

  public interface DataStore<K, V> {
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    V get(K key);

    void put(K key, V value);
  }

  private static class WeakMapDataStore<K, V> implements DataStore<K, V> {
    private final Cache<K, V> info = Cache.newBuilder().setWeakKeys().build();

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
      return info.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public V get(K key) {
      return info.get(key);
    }

    @Override
    public void put(K key, V value) {
      info.put(key, value);
    }
  }
}
