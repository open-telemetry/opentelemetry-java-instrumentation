/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;

public class VertxSqlClientRequest {

  private final String queryText;
  private final boolean parameterizedQuery;
  @Nullable private final Long operationBatchSize;
  private final VertxSqlClientInfo info;

  public VertxSqlClientRequest(
      String queryText,
      VertxSqlClientInfo info,
      boolean parameterizedQuery,
      @Nullable Long operationBatchSize) {
    this.queryText = queryText;
    this.parameterizedQuery = parameterizedQuery;
    this.operationBatchSize = operationBatchSize;
    this.info = info;
  }

  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public String getUser() {
    return getInfo().getUser();
  }

  @Nullable
  public String getDatabase() {
    return getInfo().getNamespace();
  }

  @Nullable
  public String getHost() {
    return getInfo().getLegacyServerAddress();
  }

  @Nullable
  public Integer getPort() {
    return getInfo().getLegacyServerPort();
  }

  @Nullable
  public String getConfiguredServerAddress() {
    DbServerTarget serverTarget = getInfo().getServerTarget();
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    DbServerTarget serverTarget = getInfo().getServerTarget();
    return serverTarget != null ? serverTarget.getPort() : null;
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  public String getDbSystemName() {
    return getInfo().getDbSystemName();
  }

  @Nullable
  public Long getOperationBatchSize() {
    return operationBatchSize;
  }

  protected VertxSqlClientInfo getInfo() {
    return info;
  }
}
