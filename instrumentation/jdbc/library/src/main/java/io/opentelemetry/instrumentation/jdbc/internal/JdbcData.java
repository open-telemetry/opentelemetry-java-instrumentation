/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Holds info associated with JDBC connections and prepared statements. */
public final class JdbcData {
  private static final Logger logger = LoggerFactory.getLogger(JdbcData.class);

  private static final CacheFactory cacheFactory = getCacheFactory();

  public static Cache<Connection, DbInfo> connectionInfo = cacheFactory.connectionInfoCache();
  public static Cache<PreparedStatement, String> preparedStatement =
      cacheFactory.preparedStatementCache();

  private JdbcData() {}

  private static CacheFactory getCacheFactory() {
    try {
      // this class is provided by jdbc javaagent instrumentation
      Class<?> clazz =
          Class.forName("io.opentelemetry.javaagent.instrumentation.jdbc.AgentCacheFactory");
      return (CacheFactory) clazz.getConstructor().newInstance();
    } catch (ClassNotFoundException ignored) {
      // ignored, this is expected when running as library instrumentation
    } catch (Exception exception) {
      logger.error("Failed to instantiate AgentCacheFactory", exception);
    }

    return new DefaultCacheFactory();
  }

  public interface CacheFactory {
    Cache<Connection, DbInfo> connectionInfoCache();

    Cache<PreparedStatement, String> preparedStatementCache();
  }

  private static class DefaultCacheFactory implements CacheFactory {

    @Override
    public Cache<Connection, DbInfo> connectionInfoCache() {
      return Cache.newBuilder().setWeakKeys().build();
    }

    @Override
    public Cache<PreparedStatement, String> preparedStatementCache() {
      return Cache.newBuilder().setWeakKeys().build();
    }
  }
}
