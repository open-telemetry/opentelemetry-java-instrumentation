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
  @Nullable private final SqlConnectOptions sqlConnectOptions;
  @Nullable private final VertxSqlAddressGroup addressGroup;
  private final boolean configuredServerTarget;

  public VertxSqlClientRequest(
      String queryText,
      @Nullable SqlConnectOptions sqlConnectOptions,
      boolean parameterizedQuery,
      String dbSystemName,
      @Nullable Long operationBatchSize) {
    this(
        queryText,
        sqlConnectOptions,
        parameterizedQuery,
        dbSystemName,
        operationBatchSize,
        null,
        false);
  }

  public VertxSqlClientRequest(
      String queryText,
      @Nullable SqlConnectOptions sqlConnectOptions,
      boolean parameterizedQuery,
      String dbSystemName,
      @Nullable Long operationBatchSize,
      @Nullable VertxSqlAddressGroup addressGroup) {
    this(
        queryText,
        sqlConnectOptions,
        parameterizedQuery,
        dbSystemName,
        operationBatchSize,
        addressGroup,
        true);
  }

  private VertxSqlClientRequest(
      String queryText,
      @Nullable SqlConnectOptions sqlConnectOptions,
      boolean parameterizedQuery,
      String dbSystemName,
      @Nullable Long operationBatchSize,
      @Nullable VertxSqlAddressGroup addressGroup,
      boolean configuredServerTarget) {
    this.queryText = queryText;
    this.sqlConnectOptions = sqlConnectOptions;
    this.parameterizedQuery = parameterizedQuery;
    this.dbSystemName = dbSystemName;
    this.operationBatchSize = operationBatchSize;
    this.addressGroup = addressGroup != null ? addressGroup.withDbSystem(dbSystemName) : null;
    this.configuredServerTarget = configuredServerTarget;
  }

  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public String getUser() {
    return sqlConnectOptions != null ? sqlConnectOptions.getUser() : null;
  }

  @Nullable
  public String getDatabase() {
    return sqlConnectOptions != null ? sqlConnectOptions.getDatabase() : null;
  }

  @Nullable
  public String getHost() {
    return sqlConnectOptions != null ? sqlConnectOptions.getHost() : null;
  }

  @Nullable
  public Integer getPort() {
    return sqlConnectOptions != null ? sqlConnectOptions.getPort() : null;
  }

  @Nullable
  public String getConfiguredServerAddress() {
    return addressGroup != null ? addressGroup.getAddress() : null;
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    return addressGroup != null ? addressGroup.getPort() : null;
  }

  boolean hasConfiguredServerTarget() {
    return configuredServerTarget;
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
