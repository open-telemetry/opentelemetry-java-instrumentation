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
  @Nullable private volatile SqlConnectOptions sqlConnectOptions;
  @Nullable private volatile VertxSqlAddressGroup addressGroup;
  private volatile boolean connectionDataUpdated;

  public VertxSqlClientRequest(
      String queryText,
      @Nullable SqlConnectOptions sqlConnectOptions,
      boolean parameterizedQuery,
      String dbSystemName,
      @Nullable Long operationBatchSize,
      @Nullable VertxSqlAddressGroup addressGroup) {
    this.queryText = queryText;
    this.sqlConnectOptions = sqlConnectOptions;
    this.parameterizedQuery = parameterizedQuery;
    this.dbSystemName = dbSystemName;
    this.operationBatchSize = operationBatchSize;
    this.addressGroup = addressGroup != null ? addressGroup.withDbSystem(dbSystemName) : null;
  }

  public boolean setConnectionData(VertxSqlClientData data) {
    if (addressGroup != null) {
      return false;
    }
    sqlConnectOptions = data.getConnectOptions();
    VertxSqlAddressGroup dataAddressGroup = data.getAddressGroup();
    String dataDbSystem = data.getDbSystem();
    addressGroup =
        dataAddressGroup != null
            ? dataAddressGroup.withDbSystem(dataDbSystem != null ? dataDbSystem : dbSystemName)
            : null;
    connectionDataUpdated = true;
    return true;
  }

  public boolean isConnectionDataUpdated() {
    return connectionDataUpdated;
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
