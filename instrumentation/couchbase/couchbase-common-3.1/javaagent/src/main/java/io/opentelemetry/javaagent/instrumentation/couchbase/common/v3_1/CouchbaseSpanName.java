/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import javax.annotation.Nullable;

public class CouchbaseSpanName {

  private static final String DB_COLLECTION_NAME = "db.collection.name";
  private static final String DB_NAMESPACE = "db.namespace";
  private static final String DB_OPERATION_NAME = "db.operation.name";
  private static final String DB_QUERY_SUMMARY = "db.query.summary";

  private String operation;
  @Nullable private String querySummary;
  @Nullable private String collection;
  @Nullable private String namespace;
  @Nullable private CouchbaseServerTarget serverTarget;
  private boolean databaseRequest;

  public CouchbaseSpanName(String operation) {
    this.operation = operation;
  }

  public void captureAttribute(String key, @Nullable String value) {
    if (DB_QUERY_SUMMARY.equals(key)) {
      querySummary = value;
    } else if (DB_COLLECTION_NAME.equals(key)) {
      collection = value;
    } else if (DB_NAMESPACE.equals(key)) {
      namespace = value;
    } else if (DB_OPERATION_NAME.equals(key) && value != null) {
      operation = value;
    }
  }

  public void captureServerTarget(@Nullable CouchbaseServerTarget serverTarget) {
    databaseRequest = true;
    this.serverTarget = serverTarget;
  }

  public boolean isDatabaseRequest() {
    return databaseRequest;
  }

  public String spanName() {
    if (querySummary != null) {
      return querySummary;
    }
    String target = collection;
    if (target == null) {
      target = namespace;
    }
    if (target == null && serverTarget != null) {
      target = serverTarget.getAddress();
      Integer port = serverTarget.getPort();
      if (port != null) {
        target += ":" + port;
      }
    }
    return target == null ? operation : operation + " " + target;
  }
}
