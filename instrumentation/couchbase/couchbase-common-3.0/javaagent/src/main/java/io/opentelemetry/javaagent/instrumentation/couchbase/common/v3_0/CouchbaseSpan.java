/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.time.Instant;
import javax.annotation.Nullable;

public final class CouchbaseSpan {

  private static final String DB_COUCHBASE_COLLECTION = "db.couchbase.collection";
  private static final String NET_PEER_NAME = "net.peer.name";
  private static final String NET_PEER_PORT = "net.peer.port";

  private static final boolean v3Preview = v3Preview();
  private static final boolean captureExperimentalTelemetry;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "couchbase");
    captureExperimentalTelemetry =
        config.getBoolean(
            v3Preview
                ? "emit_experimental_telemetry/development"
                : "experimental_span_attributes/development",
            false);
  }

  private final Span span;
  private final boolean makeCurrentOnEnd;
  private final boolean mapLegacyNetworkPeerAttributes;

  CouchbaseSpan(Span span, boolean makeCurrentOnEnd, boolean mapLegacyNetworkPeerAttributes) {
    this.span = span;
    this.makeCurrentOnEnd = makeCurrentOnEnd;
    this.mapLegacyNetworkPeerAttributes = mapLegacyNetworkPeerAttributes;
  }

  Span getSpan() {
    return span;
  }

  public void setAttribute(String key, @Nullable String value) {
    String stableKey = stableKey(key);
    if (emitStableDatabaseSemconv()) {
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()
        && (!v3Preview || stableKey != null || captureExperimentalAttribute(key))) {
      span.setAttribute(key, value);
    }
  }

  public void setAttribute(String key, boolean value) {
    String stableKey = stableKey(key);
    if (emitStableDatabaseSemconv()) {
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()
        && (!v3Preview || stableKey != null || captureExperimentalAttribute(key))) {
      span.setAttribute(key, value);
    }
  }

  public void setAttribute(String key, long value) {
    String stableKey = stableKey(key);
    if (emitStableDatabaseSemconv()) {
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()
        && (!v3Preview || stableKey != null || captureExperimentalAttribute(key))) {
      span.setAttribute(key, value);
    }
  }

  public void setExperimentalAttribute(String key, @Nullable String value) {
    if (!v3Preview || captureExperimentalTelemetry) {
      span.setAttribute(key, value);
    }
  }

  public void setExperimentalAttribute(String key, long value) {
    if (!v3Preview || captureExperimentalTelemetry) {
      span.setAttribute(key, value);
    }
  }

  public void setRawAttribute(String key, @Nullable String value) {
    span.setAttribute(key, value);
  }

  public void setRawAttribute(String key, long value) {
    span.setAttribute(key, value);
  }

  public void updateName(String name) {
    span.updateName(name);
  }

  public void addEvent(String name, Instant timestamp) {
    span.addEvent(name, timestamp);
  }

  public void setStatus(StatusCode statusCode) {
    span.setStatus(statusCode);
  }

  public void recordException(Throwable throwable) {
    span.recordException(throwable);
  }

  public void end() {
    if (makeCurrentOnEnd) {
      try (Scope ignored = span.makeCurrent()) {
        span.end();
      }
    } else {
      span.end();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Nullable
  private String stableKey(String key) {
    if (key.equals(DB_COUCHBASE_COLLECTION)) {
      return DB_COLLECTION_NAME.getKey();
    }
    if (key.equals(DB_NAME.getKey())) {
      return DB_NAMESPACE.getKey();
    }
    if (key.equals(DB_OPERATION.getKey())) {
      return DB_OPERATION_NAME.getKey();
    }
    if (key.equals(DB_STATEMENT.getKey())) {
      return DB_QUERY_TEXT.getKey();
    }
    if (key.equals(DB_SYSTEM.getKey())) {
      return DB_SYSTEM_NAME.getKey();
    }
    if (mapLegacyNetworkPeerAttributes && key.equals(NET_PEER_NAME)) {
      return NETWORK_PEER_ADDRESS.getKey();
    }
    if (mapLegacyNetworkPeerAttributes && key.equals(NET_PEER_PORT)) {
      return NETWORK_PEER_PORT.getKey();
    }
    return null;
  }

  private static boolean captureExperimentalAttribute(String key) {
    return captureExperimentalTelemetry && (v3Preview || key.startsWith("db.couchbase."));
  }

  static boolean emitExperimentalTelemetry() {
    return captureExperimentalTelemetry;
  }
}
