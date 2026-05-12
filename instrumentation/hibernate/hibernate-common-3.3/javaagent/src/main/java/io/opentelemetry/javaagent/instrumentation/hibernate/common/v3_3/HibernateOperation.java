/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3;

import javax.annotation.Nullable;

public class HibernateOperation {
  private final String spanName;
  @Nullable private final String sessionId;

  public HibernateOperation(
      String operation, @Nullable String entityName, @Nullable SessionInfo sessionInfo) {
    this(spanNameForOperation(operation, entityName), sessionInfo);
  }

  public HibernateOperation(String operation, @Nullable SessionInfo sessionInfo) {
    this.spanName = operation;
    this.sessionId = sessionInfo != null ? sessionInfo.getSessionId() : null;
  }

  String getName() {
    return spanName;
  }

  @Nullable
  String getSessionId() {
    return sessionId;
  }

  private static String spanNameForOperation(String operationName, @Nullable String entityName) {
    if (entityName != null) {
      return operationName + " " + entityName;
    }
    return operationName;
  }
}
