/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;

import javax.annotation.Nullable;

public class CouchbaseSpanName {

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
    if (DB_QUERY_SUMMARY.getKey().equals(key)) {
      querySummary = value;
    } else if (DB_COLLECTION_NAME.getKey().equals(key) || "db.couchbase.collection".equals(key)) {
      collection = value;
    } else if (DB_NAMESPACE.getKey().equals(key) || "db.name".equals(key)) {
      namespace = value;
    } else if (DB_OPERATION_NAME.getKey().equals(key) || "db.operation".equals(key)) {
      if (value != null) {
        operation = value;
      }
    } else if ("db.statement".equals(key)) {
      querySummary = value;
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
