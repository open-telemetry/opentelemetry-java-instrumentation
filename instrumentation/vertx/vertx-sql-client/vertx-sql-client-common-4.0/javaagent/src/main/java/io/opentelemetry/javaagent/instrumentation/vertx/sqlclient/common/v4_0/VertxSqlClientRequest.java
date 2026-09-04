/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientRequest {

  private final String queryText;
  private final boolean parameterizedQuery;
  private final String dbSystemName;
  @Nullable private final Long operationBatchSize;
  private final VertxSqlClientData clientData;

  public VertxSqlClientRequest(
      String queryText,
      @Nullable SqlConnectOptions sqlConnectOptions,
      boolean parameterizedQuery,
      String dbSystemName,
      @Nullable Long operationBatchSize) {
    this(
        queryText,
        new VertxSqlClientData(sqlConnectOptions, dbSystemName),
        parameterizedQuery,
        operationBatchSize);
  }

  public VertxSqlClientRequest(
      String queryText,
      VertxSqlClientData clientData,
      boolean parameterizedQuery,
      @Nullable Long operationBatchSize) {
    this.queryText = queryText;
    this.parameterizedQuery = parameterizedQuery;
    this.dbSystemName = clientData.getDbSystemName();
    this.operationBatchSize = operationBatchSize;
    this.clientData = clientData;
  }

  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public String getUser() {
    return clientData.getUser();
  }

  @Nullable
  public String getDatabase() {
    return clientData.getDatabase();
  }

  @Nullable
  public String getHost() {
    return clientData.getHost();
  }

  @Nullable
  public Integer getPort() {
    return clientData.getPort();
  }

  @Nullable
  public String getConfiguredServerAddress() {
    return clientData.getConfiguredServerAddress();
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    return clientData.getConfiguredServerPort();
  }

  boolean hasConfiguredServerTarget() {
    return clientData.hasConfiguredServerTarget();
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  public String getDbSystemName() {
    return dbSystemName;
  }

  @Nullable
  public Long getOperationBatchSize() {
    return operationBatchSize;
  }
}
