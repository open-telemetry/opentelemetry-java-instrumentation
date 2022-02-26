/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@AutoValue
public abstract class CouchbaseRequestInfo {

  private static final ClassValue<Map<String, String>> methodOperationNames =
      new ClassValue<Map<String, String>>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  private String peerName;
  private Integer peerPort;
  private String localAddress;
  private String operationId;

  public static CouchbaseRequestInfo create(
      @Nullable String bucket, Class<?> declaringClass, String methodName) {
    String operation =
        methodOperationNames
            .get(declaringClass)
            .computeIfAbsent(methodName, m -> computeOperation(declaringClass, m));
    return new AutoValue_CouchbaseRequestInfo(bucket, null, operation, true);
  }

  public static CouchbaseRequestInfo create(@Nullable String bucket, Object query) {
    SqlStatementInfo statement = CouchbaseQuerySanitizer.sanitize(query);

    return new AutoValue_CouchbaseRequestInfo(
        bucket, statement.getFullStatement(), statement.getOperation(), false);
  }

  private static String computeOperation(Class<?> declaringClass, String methodName) {
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + methodName;
  }

  @Nullable
  public abstract String bucket();

  @Nullable
  public abstract String statement();

  @Nullable
  public abstract String operation();

  public abstract boolean isMethodCall();

  @Nullable
  public String getPeerName() {
    return peerName;
  }

  public void setPeerName(String peerName) {
    this.peerName = peerName;
  }

  @Nullable
  public Integer getPeerPort() {
    return peerPort;
  }

  public void setPeerPort(Integer peerPort) {
    this.peerPort = peerPort;
  }

  @Nullable
  public String getLocalAddress() {
    return localAddress;
  }

  public void setLocalAddress(String localAddress) {
    this.localAddress = localAddress;
  }

  @Nullable
  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
  }
}
