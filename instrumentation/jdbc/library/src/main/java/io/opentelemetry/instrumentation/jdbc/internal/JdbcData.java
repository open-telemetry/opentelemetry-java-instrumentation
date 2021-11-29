/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.WeakHashMap;

/** Holds info associated with JDBC connections and prepared statements. */
public final class JdbcData {

  private static final Map<DbInfo, WeakReference<DbInfo>> dbInfos = new WeakHashMap<>();
  public static final VirtualField<Connection, DbInfo> connectionInfo =
      VirtualField.find(Connection.class, DbInfo.class);
  public static final VirtualField<PreparedStatement, String> preparedStatement =
      VirtualField.find(PreparedStatement.class, String.class);

  private JdbcData() {}

  /**
   * Returns canonical representation of db info.
   *
   * @param dbInfo db info to canonicalize
   * @return db info with same content as input db info. If two equal inputs are given to this
   *     method, both calls will return the same instance. This method may return one instance now
   *     and a different instance later if the original interned instance was garbage collected.
   */
  public static DbInfo intern(DbInfo dbInfo) {
    synchronized (dbInfos) {
      WeakReference<DbInfo> reference = dbInfos.get(dbInfo);
      if (reference != null) {
        DbInfo result = reference.get();
        if (result != null) {
          return result;
        }
      }
      dbInfos.put(dbInfo, new WeakReference<>(dbInfo));
      return dbInfo;
    }
  }
}
