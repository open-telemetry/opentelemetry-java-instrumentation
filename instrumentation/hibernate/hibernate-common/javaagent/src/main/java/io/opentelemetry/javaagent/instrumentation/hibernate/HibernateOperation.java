/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

public class HibernateOperation {
  private final String spanName;
  private final String sessionId;

  public static HibernateOperation fromOperationName(
      String operationName, String entityName, SessionInfo sessionInfo) {
    return fromSpanName(spanNameForOperation(operationName, entityName), sessionInfo);
  }

  public static HibernateOperation fromSpanName(String spanName, SessionInfo sessionInfo) {
    return new HibernateOperation(spanName, sessionInfo);
  }

  private HibernateOperation(String spanName, SessionInfo sessionInfo) {
    this.spanName = spanName;
    this.sessionId = sessionInfo != null ? sessionInfo.getSessionId() : null;
  }

  public String getSpanName() {
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
