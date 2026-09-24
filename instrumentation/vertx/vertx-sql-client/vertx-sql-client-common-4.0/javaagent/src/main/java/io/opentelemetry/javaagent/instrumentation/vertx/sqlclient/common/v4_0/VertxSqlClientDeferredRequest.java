/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import javax.annotation.Nullable;

public class VertxSqlClientDeferredRequest extends VertxSqlClientRequest {

  @Nullable private volatile VertxSqlClientInfo capturedInfo;
  private boolean frozen;

  public VertxSqlClientDeferredRequest(
      String queryText,
      VertxSqlClientInfo info,
      boolean parameterizedQuery,
      @Nullable Long operationBatchSize) {
    super(queryText, info, parameterizedQuery, operationBatchSize);
  }

  public synchronized boolean replaceInfo(VertxSqlClientInfo info) {
    if (frozen || capturedInfo != null) {
      return false;
    }
    capturedInfo = info;
    return true;
  }

  synchronized boolean freezeInfo() {
    frozen = true;
    return capturedInfo != null;
  }

  boolean isInfoUpdated() {
    return capturedInfo != null;
  }

  @Override
  protected VertxSqlClientInfo getInfo() {
    VertxSqlClientInfo info = capturedInfo;
    return info != null ? info : super.getInfo();
  }
}
