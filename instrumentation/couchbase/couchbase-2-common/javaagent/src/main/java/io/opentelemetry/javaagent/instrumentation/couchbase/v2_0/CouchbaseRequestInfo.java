/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.context.ContextKey.named;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@AutoValue
public abstract class CouchbaseRequestInfo {

  private static final ContextKey<CouchbaseRequestInfo> KEY =
      named("opentelemetry-couchbase-request-key");

  private static final ClassValue<Map<String, String>> methodOperationNames =
      new ClassValue<Map<String, String>>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
          return new ConcurrentHashMap<>();
        }
      };

  private String localAddress;
  private String operationId;
  private SocketAddress peerAddress;

  public static CouchbaseRequestInfo create(
      @Nullable String bucket, Class<?> declaringClass, String methodName) {
    String operation =
        methodOperationNames
            .get(declaringClass)
            .computeIfAbsent(methodName, m -> computeOperation(declaringClass, m));
    return new AutoValue_CouchbaseRequestInfo(bucket, null, null, operation, true);
  }

  public static CouchbaseRequestInfo create(@Nullable String bucket, Object query) {
    SqlQuery sqlQuery = emitOldDatabaseSemconv() ? CouchbaseQuerySanitizer.analyze(query) : null;
    SqlQuery sqlQueryWithSummary =
        emitStableDatabaseSemconv() ? CouchbaseQuerySanitizer.analyzeWithSummary(query) : null;
    String operation = sqlQuery != null ? sqlQuery.getOperationName() : null;
    return new AutoValue_CouchbaseRequestInfo(
        bucket, sqlQuery, sqlQueryWithSummary, operation, false);
  }

  private static String computeOperation(Class<?> declaringClass, String methodName) {
    String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    return className + "." + methodName;
  }

  public static Context init(Context context, CouchbaseRequestInfo couchbaseRequest) {
    return context.with(KEY, couchbaseRequest);
  }

  @Nullable
  public static CouchbaseRequestInfo get(Context context) {
    return context.get(KEY);
  }

  @Nullable
  public abstract String bucket();

  @Nullable
  public abstract SqlQuery getSqlQuery();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();

  @Nullable
  public abstract String operation();

  public abstract boolean isMethodCall();

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

  @Nullable
  public SocketAddress getPeerAddress() {
    return peerAddress;
  }

  public void setPeerAddress(SocketAddress peerAddress) {
    this.peerAddress = peerAddress;
  }
}
