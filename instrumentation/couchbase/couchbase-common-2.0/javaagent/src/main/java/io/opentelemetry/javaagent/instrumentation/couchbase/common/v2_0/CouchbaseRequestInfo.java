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
  @Nullable private volatile Endpoint endpoint;

  public static CouchbaseRequestInfo create(
      @Nullable String bucket, Class<?> declaringClass, String methodName) {
    String operation =
        methodOperationNames
            .get(declaringClass)
            .computeIfAbsent(methodName, m -> computeOperation(declaringClass, m));
    return new AutoValue_CouchbaseRequestInfo(bucket, null, null, operation, true);
  }

  @SuppressWarnings("deprecation") // using deprecated old semconv operation
  public static CouchbaseRequestInfo create(@Nullable String bucket, Object query) {
    SqlQuery sqlQuery = emitOldDatabaseSemconv() ? CouchbaseQuerySanitizer.analyze(query) : null;
    SqlQuery sqlQueryWithSummary =
        emitStableDatabaseSemconv() ? CouchbaseQuerySanitizer.analyzeWithSummary(query) : null;
    String operation = sqlQuery != null ? sqlQuery.getOperationName() : null;
    if (operation == null && sqlQueryWithSummary != null) {
      operation = sqlQueryWithSummary.getOperationName();
    }
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
  public abstract String getBucket();

  @Nullable
  public abstract SqlQuery getSqlQuery();

  @Nullable
  public abstract SqlQuery getSqlQueryWithSummary();

  @Nullable
  public abstract String getOperation();

  public abstract boolean isMethodCall();

  // Network instrumentation adds mutable endpoint and operation state to the request, so each
  // subscription must receive its own copy.
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
        getBucket(), getSqlQuery(), getSqlQueryWithSummary(), getOperation(), isMethodCall());
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
  public Endpoint getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(@Nullable SocketAddress peerAddress, String remoteAddress) {
    if (peerAddress == null) {
      return;
    }

    int portSeparator = remoteAddress.lastIndexOf(':');
    String serverAddress = remoteAddress.substring(0, portSeparator);
    if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
      serverAddress = serverAddress.substring(1, serverAddress.length() - 1);
    }
    int serverPort = Integer.parseInt(remoteAddress.substring(portSeparator + 1));
    endpoint = new Endpoint(peerAddress, serverAddress, serverPort);
  }

  public static final class Endpoint {
    private final SocketAddress peerAddress;
    private final String serverAddress;
    private final int serverPort;

    private Endpoint(SocketAddress peerAddress, String serverAddress, int serverPort) {
      this.peerAddress = peerAddress;
      this.serverAddress = serverAddress;
      this.serverPort = serverPort;
    }

    public SocketAddress getPeerAddress() {
      return peerAddress;
    }

    public String getServerAddress() {
      return serverAddress;
    }

    public int getServerPort() {
      return serverPort;
    }
  }
}
