/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.connectionFromStatement;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.extractDbInfo;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class DbRequest {

  @Nullable
  public static DbRequest create(
      PreparedStatement statement, Map<String, String> preparedStatementParameters) {
    return create(
        statement, JdbcData.preparedStatement.get(statement), preparedStatementParameters);
  }

  @Nullable
  public static DbRequest create(Statement statement, String dbStatementString) {
    return create(statement, dbStatementString, null, Collections.emptyMap());
  }

  @Nullable
  public static DbRequest create(
      Statement statement,
      String dbStatementString,
      Map<String, String> preparedStatementParameters) {
    return create(statement, dbStatementString, null, preparedStatementParameters);
  }

  @Nullable
  public static DbRequest create(
      Statement statement,
      String dbStatementString,
      Long batchSize,
      Map<String, String> preparedStatementParameters) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    return create(
        extractDbInfo(connection), dbStatementString, batchSize, preparedStatementParameters);
  }

  public static DbRequest create(
      Statement statement, Collection<String> queryTexts, Long batchSize) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    return create(extractDbInfo(connection), queryTexts, batchSize, Collections.emptyMap());
  }

  public static DbRequest create(DbInfo dbInfo, String queryText) {
    return create(dbInfo, queryText, null, Collections.emptyMap());
  }

  public static DbRequest create(
      DbInfo dbInfo,
      String queryText,
      Long batchSize,
      Map<String, String> preparedStatementParameters) {
    return create(
        dbInfo, Collections.singletonList(queryText), batchSize, preparedStatementParameters);
  }

  public static DbRequest create(
      DbInfo dbInfo,
      Collection<String> queryTexts,
      Long batchSize,
      Map<String, String> preparedStatementParameters) {
    return new AutoValue_DbRequest(dbInfo, queryTexts, batchSize, preparedStatementParameters);
  }

  public abstract DbInfo getDbInfo();

  public abstract Collection<String> getQueryTexts();

  @Nullable
  public abstract Long getBatchSize();

  @Nullable
  public abstract Map<String, String> getPreparedStatementParameters();
}
