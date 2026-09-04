/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.msg.RequestContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseSpanName;
import io.opentelemetry.javaagent.instrumentation.couchbase.v3_1.shaded.com.couchbase.client.tracing.opentelemetry.OpenTelemetryRequestSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.v3_1.shaded.com.couchbase.client.tracing.opentelemetry.OpenTelemetryRequestTracer;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class CouchbaseRequestTracer implements RequestTracer {

  private static final String DB_COUCHBASE_COLLECTION = "db.couchbase.collection";
  private static final String NET_PEER_NAME = "net.peer.name";
  private static final String NET_PEER_PORT = "net.peer.port";

  private static final boolean captureExperimentalAttributes =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "couchbase")
          .getBoolean("experimental_span_attributes/development", false);

  private final RequestTracer delegate;

  public static RequestTracer create(Tracer tracer) {
    return new CouchbaseRequestTracer(OpenTelemetryRequestTracer.wrap(tracer));
  }

  private CouchbaseRequestTracer(RequestTracer delegate) {
    this.delegate = delegate;
  }

  @Override
  public RequestSpan requestSpan(String name, RequestSpan parent) {
    RequestSpan unwrappedParent = parent;
    if (parent instanceof TranslatingRequestSpan) {
      unwrappedParent = ((TranslatingRequestSpan) parent).delegate;
    }
    return new TranslatingRequestSpan(name, delegate.requestSpan(name, unwrappedParent));
  }

  @Override
  public Mono<Void> start() {
    return delegate.start();
  }

  @Override
  public Mono<Void> stop(Duration timeout) {
    return delegate.stop(timeout);
  }

  private static final class TranslatingRequestSpan implements RequestSpan {

    private final CouchbaseSpanName spanName;
    private final RequestSpan delegate;

    private TranslatingRequestSpan(String name, RequestSpan delegate) {
      spanName = new CouchbaseSpanName(name);
      this.delegate = delegate;
    }

    @Override
    public void setAttribute(String key, String value) {
      if (emitStableDatabaseSemconv()) {
        String stableKey = stableKey(key);
        if (stableKey != null) {
          spanName.captureAttribute(stableKey, value);
          delegate.setAttribute(stableKey, value);
        } else if (captureExperimentalAttribute(key)) {
          delegate.setAttribute(key, value);
        }
      }
      if (emitOldDatabaseSemconv()) {
        delegate.setAttribute(key, value);
      }
    }

    @Override
    // This wrapper method delegates to the same RequestSpan overload, which is absent from
    // Couchbase 3.1.0-3.1.2. Suppressing muzzle is safe because those older clients only call
    // the string overload that exists in their RequestSpan API.
    @NoMuzzle
    public void setAttribute(String key, boolean value) {
      if (emitStableDatabaseSemconv()) {
        String stableKey = stableKey(key);
        if (stableKey != null) {
          delegate.setAttribute(stableKey, value);
        } else if (captureExperimentalAttribute(key)) {
          delegate.setAttribute(key, value);
        }
      }
      if (emitOldDatabaseSemconv()) {
        delegate.setAttribute(key, value);
      }
    }

    @Override
    // This wrapper method delegates to the same RequestSpan overload, which is absent from
    // Couchbase 3.1.0-3.1.2. Suppressing muzzle is safe because those older clients only call
    // the string overload that exists in their RequestSpan API.
    @NoMuzzle
    public void setAttribute(String key, long value) {
      if (emitStableDatabaseSemconv()) {
        String stableKey = stableKey(key);
        if (stableKey != null) {
          delegate.setAttribute(stableKey, value);
        } else if (captureExperimentalAttribute(key)) {
          delegate.setAttribute(key, value);
        }
      }
      if (emitOldDatabaseSemconv()) {
        delegate.setAttribute(key, value);
      }
    }

    @Override
    public void addEvent(String name, Instant timestamp) {
      delegate.addEvent(name, timestamp);
    }

    @Override
    public void end() {
      if (emitStableDatabaseSemconv() && spanName.isDatabaseRequest()) {
        ((OpenTelemetryRequestSpan) delegate).span().updateName(spanName.spanName());
      }
      delegate.end();
    }

    @Override
    public void requestContext(RequestContext requestContext) {
      delegate.requestContext(requestContext);
      // the old conventions never described a server for Couchbase, and they are frozen
      if (emitStableDatabaseSemconv()) {
        setConfiguredTarget(spanName, delegate, CouchbaseServerTargets.get(requestContext.core()));
      }
    }

    private static void setConfiguredTarget(
        CouchbaseSpanName spanName, RequestSpan span, @Nullable CouchbaseServerTarget target) {
      spanName.captureServerTarget(target);
      if (target == null) {
        return;
      }
      OpenTelemetryRequestSpan openTelemetrySpan = (OpenTelemetryRequestSpan) span;
      span.setAttribute(SERVER_ADDRESS.getKey(), target.getAddress());
      Integer port = target.getPort();
      if (port != null) {
        // RequestSpan does not expose long attributes throughout the supported range, while the
        // shaded implementation does.
        openTelemetrySpan.setAttribute(SERVER_PORT.getKey(), port.longValue());
      }
    }

    @SuppressWarnings("deprecation") // using deprecated semconv
    private static String stableKey(String key) {
      if (key.equals(DB_COLLECTION_NAME.getKey())
          || key.equals(DB_NAMESPACE.getKey())
          || key.equals(DB_OPERATION_NAME.getKey())
          || key.equals(DB_QUERY_TEXT.getKey())
          || key.equals(DB_SYSTEM_NAME.getKey())
          || key.equals(NETWORK_PEER_ADDRESS.getKey())
          || key.equals(NETWORK_PEER_PORT.getKey())
          || key.equals(DB_QUERY_SUMMARY.getKey())) {
        return key;
      }
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
}
