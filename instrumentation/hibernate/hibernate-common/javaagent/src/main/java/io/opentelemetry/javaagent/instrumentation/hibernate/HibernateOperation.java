/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

public class HibernateOperation {
  private final String spanName;
  private final String sessionId;

  public HibernateOperation(String operation, String entityName, SessionInfo sessionInfo) {
    this(spanNameForOperation(operation, entityName), sessionInfo);
  }

  public HibernateOperation(String operation, SessionInfo sessionInfo) {
    this.spanName = operation;
    this.sessionId = sessionInfo != null ? sessionInfo.getSessionId() : null;
  }

  public String getName() {
    return spanName;
  }

  public String getSessionId() {
    return sessionId;
  }

  private static String spanNameForOperation(String operationName, String entityName) {
    if (entityName != null) {
      return operationName + " " + entityName;
    }
    return operationName;
  }
}
