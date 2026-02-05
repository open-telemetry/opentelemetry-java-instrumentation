/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.connectionFromStatement;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.extractDbInfo;
import static java.util.Collections.emptyMap;

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
        statement,
        JdbcData.preparedStatement.get(statement),
        null,
        preparedStatementParameters,
        true);
  }

  @Nullable
  public static DbRequest create(Statement statement, String dbStatementString) {
    return create(statement, dbStatementString, null, emptyMap(), false);
  }

  @Nullable
  public static DbRequest create(
      Statement statement,
      String dbStatementString,
      Long batchSize,
      Map<String, String> preparedStatementParameters,
      boolean parameterizedQuery) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    return create(
        extractDbInfo(connection),
        dbStatementString,
        batchSize,
        preparedStatementParameters,
        parameterizedQuery);
  }

  public static DbRequest create(
      Statement statement,
      Collection<String> queryTexts,
      Long batchSize,
      boolean parameterizedQuery) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    return create(extractDbInfo(connection), queryTexts, batchSize, emptyMap(), parameterizedQuery);
  }

  public static DbRequest create(DbInfo dbInfo, String queryText, boolean parameterizedQuery) {
    return create(dbInfo, queryText, null, emptyMap(), parameterizedQuery);
  }

  public static DbRequest create(
      DbInfo dbInfo,
      String queryText,
      Long batchSize,
      Map<String, String> preparedStatementParameters,
      boolean parameterizedQuery) {
    return create(
        dbInfo,
        Collections.singletonList(queryText),
        batchSize,
        preparedStatementParameters,
        parameterizedQuery);
  }

  public static DbRequest create(
      DbInfo dbInfo,
      Collection<String> queryTexts,
      Long batchSize,
      Map<String, String> preparedStatementParameters,
      boolean parameterizedQuery) {
    return create(
        dbInfo, queryTexts, batchSize, null, preparedStatementParameters, parameterizedQuery);
  }

  private static DbRequest create(
      DbInfo dbInfo,
      Collection<String> queryTexts,
      Long batchSize,
      String operationName,
      Map<String, String> preparedStatementParameters,
      boolean parameterizedQuery) {
    return new AutoValue_DbRequest(
        dbInfo,
        queryTexts,
        batchSize,
        operationName,
        preparedStatementParameters,
        parameterizedQuery);
  }

  @Nullable
  public static DbRequest createTransaction(Connection connection, String operationName) {
    Connection realConnection = JdbcUtils.unwrapConnection(connection);
    if (realConnection == null) {
      return null;
    }

    return createTransaction(JdbcUtils.extractDbInfo(realConnection), operationName);
  }

  public static DbRequest createTransaction(DbInfo dbInfo, String operationName) {
    return create(dbInfo, Collections.emptyList(), null, operationName, emptyMap(), false);
  }

  public abstract DbInfo getDbInfo();

  public abstract Collection<String> getQueryTexts();

  @Nullable
  public abstract Long getBatchSize();

  // used for transaction instrumentation
  @Nullable
  public abstract String getOperationName();

  public abstract Map<String, String> getPreparedStatementParameters();

  public abstract boolean isParameterizedQuery();
}
