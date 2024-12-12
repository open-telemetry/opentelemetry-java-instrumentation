/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holds info associated with JDBC connections and prepared statements.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JdbcData {

  private static final Map<DbInfo, WeakReference<DbInfo>> dbInfos = new WeakHashMap<>();
  public static final VirtualField<Connection, DbInfo> connectionInfo =
      VirtualField.find(Connection.class, DbInfo.class);
  public static final VirtualField<PreparedStatement, String> preparedStatement =
      VirtualField.find(PreparedStatement.class, String.class);
  private static final VirtualField<Statement, StatementBatchInfo> statementBatch =
      VirtualField.find(Statement.class, StatementBatchInfo.class);
  private static final VirtualField<PreparedStatement, PreparedStatementBatchInfo>
      preparedStatementBatch =
          VirtualField.find(PreparedStatement.class, PreparedStatementBatchInfo.class);

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

  public static void addStatementBatch(Statement statement, String sql) {
    StatementBatchInfo batchInfo = statementBatch.get(statement);
    if (batchInfo == null) {
      batchInfo = new StatementBatchInfo();
      statementBatch.set(statement, batchInfo);
    }
    batchInfo.add(sql);
  }

  public static void addPreparedStatementBatch(PreparedStatement statement) {
    PreparedStatementBatchInfo batchInfo = preparedStatementBatch.get(statement);
    if (batchInfo == null) {
      batchInfo = new PreparedStatementBatchInfo();
      preparedStatementBatch.set(statement, batchInfo);
    }
    batchInfo.add();
  }

  public static void clearBatch(Statement statement) {
    if (statement instanceof PreparedStatement) {
      preparedStatementBatch.set((PreparedStatement) statement, null);
    } else {
      statementBatch.set(statement, null);
    }
  }

  public static StatementBatchInfo getStatementBatchInfo(Statement statement) {
    return statementBatch.get(statement);
  }

  public static Long getPreparedStatementBatchSize(PreparedStatement statement) {
    PreparedStatementBatchInfo batchInfo = preparedStatementBatch.get(statement);
    return batchInfo != null ? batchInfo.getBatchSize() : null;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class StatementBatchInfo {
    private final List<String> statements = new ArrayList<>();

    void add(String sql) {
      statements.add(sql);
    }

    public Collection<String> getStatements() {
      return statements;
    }

    public long getBatchSize() {
      return statements.size();
    }
  }

  private static final class PreparedStatementBatchInfo {
    private long batchSize;

    void add() {
      batchSize++;
    }

    long getBatchSize() {
      return batchSize;
    }
  }
}
