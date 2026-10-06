/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import static io.opentelemetry.context.ContextKey.named;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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

  @Nullable private String localAddress;
  @Nullable private String operationId;
  @Nullable private volatile Node node;

  public static CouchbaseRequestInfo create(
      @Nullable String bucket,
      @Nullable DbServerTarget serverTarget,
      Class<?> declaringClass,
      String methodName) {
    String operation =
        methodOperationNames
            .get(declaringClass)
            .computeIfAbsent(methodName, m -> computeOperation(declaringClass, m));
    return new AutoValue_CouchbaseRequestInfo(bucket, null, operation, serverTarget);
  }

  @SuppressWarnings("deprecation") // SqlQuery.getOperationName supplies db.operation.name
  public static CouchbaseRequestInfo create(
      @Nullable String bucket, @Nullable DbServerTarget serverTarget, Object query) {
    SqlQuery sqlQueryWithSummary = CouchbaseQuerySanitizer.analyzeWithSummary(query);
    String operation = sqlQueryWithSummary.getOperationName();
    return new AutoValue_CouchbaseRequestInfo(bucket, sqlQueryWithSummary, operation, serverTarget);
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
  public abstract String getBucket();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();

  @Nullable
  public abstract String getOperation();

  @Nullable
  public abstract DbServerTarget getServerTarget();

  // Each subscription needs independent mutable node state.
  public Supplier<CouchbaseRequestInfo> copySupplier() {
    return new Supplier<CouchbaseRequestInfo>() {
      @Override
      public CouchbaseRequestInfo get() {
        return copy();
      }
    };
  }

  private CouchbaseRequestInfo copy() {
    return new AutoValue_CouchbaseRequestInfo(
        getBucket(), getSqlQueryWithSummary(), getOperation(), getServerTarget());
  }

  @Nullable
  public String getLocalAddress() {
    return localAddress;
  }

  public void setLocalAddress(@Nullable String localAddress) {
    this.localAddress = localAddress;
  }

  @Nullable
  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(@Nullable String operationId) {
    this.operationId = operationId;
  }

  @Nullable
  public Node getNode() {
    return node;
  }

  public void setNode(@Nullable SocketAddress peerAddress) {
    if (peerAddress == null) {
      return;
    }
    node = new Node(peerAddress);
  }

  public static final class Node {

    private final SocketAddress peerAddress;

    private Node(SocketAddress peerAddress) {
      this.peerAddress = peerAddress;
    }

    public SocketAddress getPeerAddress() {
      return peerAddress;
    }
  }
}
