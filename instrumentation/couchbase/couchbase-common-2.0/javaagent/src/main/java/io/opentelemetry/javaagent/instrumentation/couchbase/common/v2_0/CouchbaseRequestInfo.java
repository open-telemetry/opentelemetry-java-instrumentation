/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import static io.opentelemetry.context.ContextKey.named;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.google.auto.value.AutoValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
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
      @Nullable CouchbaseServerTarget serverTarget,
      Class<?> declaringClass,
      String methodName) {
    String operation =
        methodOperationNames
            .get(declaringClass)
            .computeIfAbsent(methodName, m -> computeOperation(declaringClass, m));
    return new AutoValue_CouchbaseRequestInfo(bucket, null, null, operation, true, serverTarget);
  }

  @SuppressWarnings("deprecation") // using deprecated old semconv operation
  public static CouchbaseRequestInfo create(
      @Nullable String bucket, @Nullable CouchbaseServerTarget serverTarget, Object query) {
    SqlQuery sqlQuery = emitOldDatabaseSemconv() ? CouchbaseQuerySanitizer.analyze(query) : null;
    SqlQuery sqlQueryWithSummary =
        emitStableDatabaseSemconv() ? CouchbaseQuerySanitizer.analyzeWithSummary(query) : null;
    String operation = sqlQuery != null ? sqlQuery.getOperationName() : null;
    if (operation == null && sqlQueryWithSummary != null) {
      operation = sqlQueryWithSummary.getOperationName();
    }
    return new AutoValue_CouchbaseRequestInfo(
        bucket, sqlQuery, sqlQueryWithSummary, operation, false, serverTarget);
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
  public abstract SqlQuery getSqlQuery();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();

  @Nullable
  public abstract String getOperation();

  public abstract boolean isMethodCall();

  @Nullable
  public abstract CouchbaseServerTarget getServerTarget();

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
        getBucket(),
        getSqlQuery(),
        getSqlQueryWithSummary(),
        getOperation(),
        isMethodCall(),
        getServerTarget());
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

  public void setNode(@Nullable SocketAddress peerAddress, @Nullable String backendAddress) {
    if (peerAddress == null) {
      return;
    }
    // Replace both values atomically so retries cannot pair data from different nodes.
    node = new Node(peerAddress, backendAddress);
  }

  public static class Node {

    private final SocketAddress peerAddress;
    @Nullable private final String backendAddress;
    private final int backendPort;

    private Node(SocketAddress peerAddress, @Nullable String backendAddress) {
      this.peerAddress = peerAddress;
      int portSeparator = backendAddress == null ? -1 : backendAddress.lastIndexOf(':');
      if (portSeparator < 0) {
        this.backendAddress = stripBrackets(backendAddress);
        this.backendPort = 0;
      } else {
        this.backendAddress = stripBrackets(backendAddress.substring(0, portSeparator));
        this.backendPort = parsePort(backendAddress.substring(portSeparator + 1));
      }
    }

    public SocketAddress getPeerAddress() {
      return peerAddress;
    }

    @Nullable
    public String getBackendAddress() {
      return backendAddress;
    }

    public int getBackendPort() {
      return backendPort;
    }

    @Nullable
    private static String stripBrackets(@Nullable String host) {
      if (host == null) {
        return null;
      }
      String stripped =
          host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
      return stripped.isEmpty() ? null : stripped;
    }

    private static int parsePort(String port) {
      try {
        return Integer.parseInt(port);
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }
  }
}
