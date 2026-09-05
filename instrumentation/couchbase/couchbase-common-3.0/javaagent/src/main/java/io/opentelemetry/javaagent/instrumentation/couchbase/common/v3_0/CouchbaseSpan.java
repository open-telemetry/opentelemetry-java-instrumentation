/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
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

  private static final boolean captureExperimentalAttributes =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "couchbase")
          .getBoolean("experimental_span_attributes/development", false);

  private final Span span;
  private final boolean makeCurrentOnEnd;

  CouchbaseSpan(Span span, boolean makeCurrentOnEnd) {
    this.span = span;
    this.makeCurrentOnEnd = makeCurrentOnEnd;
  }

  Span getSpan() {
    return span;
  }

  public void setAttribute(String key, @Nullable String value) {
    if (value == null) {
      return;
    }
    if (emitStableDatabaseSemconv()) {
      String stableKey = stableKey(key);
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()) {
      span.setAttribute(key, value);
    }
  }

  public void setAttribute(String key, boolean value) {
    if (emitStableDatabaseSemconv()) {
      String stableKey = stableKey(key);
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()) {
      span.setAttribute(key, value);
    }
  }

  public void setAttribute(String key, long value) {
    if (emitStableDatabaseSemconv()) {
      String stableKey = stableKey(key);
      if (stableKey != null) {
        span.setAttribute(stableKey, value);
      } else if (captureExperimentalAttribute(key)) {
        span.setAttribute(key, value);
      }
    }
    if (emitOldDatabaseSemconv()) {
      span.setAttribute(key, value);
    }
  }

  public void setRawAttribute(String key, @Nullable String value) {
    if (value != null) {
      span.setAttribute(key, value);
    }
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
  private static String stableKey(String key) {
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
    if (key.equals(NET_PEER_NAME)) {
      return NETWORK_PEER_ADDRESS.getKey();
    }
    if (key.equals(NET_PEER_PORT)) {
      return NETWORK_PEER_PORT.getKey();
    }
    return null;
  }

  private static boolean captureExperimentalAttribute(String key) {
    return captureExperimentalAttributes && key.startsWith("db.couchbase.");
  }
}
