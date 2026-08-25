/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.vertx.sqlclient.SqlConnectOptions;
import javax.annotation.Nullable;

public class VertxSqlClientRequest {

  private final String queryText;
  @Nullable private final SqlConnectOptions sqlConnectOptions;
  private final boolean parameterizedQuery;
  private final String dbSystemName;
  @Nullable private final Long operationBatchSize;
  @Nullable private final VertxSqlAddressGroup addressGroup;

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
    this.addressGroup = addressGroup;
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

  /**
   * The complete configured target when the client was given more than one server, e.g. {@code
   * h1:5432,h2:5432}. Null when the client targets a single server, or when no complete target can
   * be rendered.
   */
  @Nullable
  public String getServerAddressGroup() {
    return addressGroup != null ? addressGroup.getAddress() : null;
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
