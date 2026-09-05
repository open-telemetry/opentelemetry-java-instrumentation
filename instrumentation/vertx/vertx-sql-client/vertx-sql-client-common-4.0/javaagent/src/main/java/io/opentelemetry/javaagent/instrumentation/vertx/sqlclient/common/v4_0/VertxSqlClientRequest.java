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
  private final VertxSqlClientInfo initialInfo;
  private volatile VertxSqlClientInfo info;

  public VertxSqlClientRequest(
      String queryText,
      VertxSqlClientInfo info,
      boolean parameterizedQuery,
      @Nullable Long operationBatchSize) {
    this.queryText = queryText;
    this.parameterizedQuery = parameterizedQuery;
    this.operationBatchSize = operationBatchSize;
    this.initialInfo = info;
    this.info = info;
  }

  public synchronized boolean replaceInfo(VertxSqlClientInfo info) {
    if (this.info.isConfigurationCaptured()) {
      return false;
    }
    this.info = info;
    return true;
  }

  public boolean isInfoUpdated() {
    return info != initialInfo;
  }

  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public String getUser() {
    return info.getUser();
  }

  @Nullable
  public String getDatabase() {
    return info.getNamespace();
  }

  @Nullable
  public String getHost() {
    return info.getLegacyServerAddress();
  }

  @Nullable
  public Integer getPort() {
    return info.getLegacyServerPort();
  }

  @Nullable
  public String getConfiguredServerAddress() {
    DbServerTarget serverTarget = info.getServerTarget();
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  public Integer getConfiguredServerPort() {
    DbServerTarget serverTarget = info.getServerTarget();
    return serverTarget != null ? serverTarget.getPort() : null;
  }

  public boolean isServerTargetCaptured() {
    return info.isServerTargetCaptured();
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  public String getDbSystemName() {
    return info.getDbSystemName();
  }

  @Nullable
  public Long getOperationBatchSize() {
    return operationBatchSize;
  }
}
