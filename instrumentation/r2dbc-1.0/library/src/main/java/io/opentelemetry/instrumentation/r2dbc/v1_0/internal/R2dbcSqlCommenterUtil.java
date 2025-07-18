/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SqlCommenterUtil;
import io.r2dbc.proxy.callback.ProxyConfig;
import io.r2dbc.proxy.core.ConnectionInfo;
import io.r2dbc.proxy.core.StatementInfo;
import io.r2dbc.proxy.core.ValueStore;
import io.r2dbc.proxy.listener.BindParameterConverter;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class R2dbcSqlCommenterUtil {
  private static final String KEY_ORIGINAL_QUERY_MAP = "originalQueryMap";

  public static void configure(ProxyConfig proxyConfig) {
    proxyConfig.setBindParameterConverter(
        new BindParameterConverter() {
          @Override
          public String onCreateStatement(String query, StatementInfo info) {
            String modifiedQuery = SqlCommenterUtil.processQuery(query);
            if (!modifiedQuery.equals(query)) {
              // We store mapping from the modified query to original query on the connection
              // the assumption here is that since the connection is not thread safe it won't be
              // used concurrently.
              storeQuery(info.getConnectionInfo(), modifiedQuery, query);
            }
            return modifiedQuery;
          }
        });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> getOriginalQueryMap(ValueStore valueStore) {
    return valueStore.get(KEY_ORIGINAL_QUERY_MAP, Map.class);
  }

  private static void storeQuery(
      ConnectionInfo connectionInfo, String modifiedQuery, String originalQuery) {
    ValueStore valueStore = connectionInfo.getValueStore();
    Map<String, String> queryMap = getOriginalQueryMap(valueStore);
    if (queryMap == null) {
      queryMap = new HashMap<>();
      valueStore.put(KEY_ORIGINAL_QUERY_MAP, queryMap);
    }
    queryMap.put(modifiedQuery, originalQuery);
  }

  static String getOriginalQuery(ConnectionInfo connectionInfo, String query) {
    Map<String, String> queryMap = getOriginalQueryMap(connectionInfo.getValueStore());
    if (queryMap == null) {
      return query;
    }
    return queryMap.getOrDefault(query, query);
  }

  static void clearQueries(ConnectionInfo connectionInfo) {
    connectionInfo.getValueStore().remove(KEY_ORIGINAL_QUERY_MAP);
  }

  private R2dbcSqlCommenterUtil() {}
}
