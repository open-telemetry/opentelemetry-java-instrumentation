/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_1;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;

import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.cnc.RequestTracer;
import com.couchbase.client.core.cnc.TracingIdentifiers;
import com.couchbase.client.core.msg.RequestContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0.CouchbaseTracer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConfiguredTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Peer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseSpanName;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

public final class CouchbaseRequestTracer implements RequestTracer {

  private final CouchbaseTracer tracer;

  public static RequestTracer create(Tracer tracer, boolean legacyBridge) {
    return new CouchbaseRequestTracer(
        new CouchbaseTracer(tracer, !legacyBridge, INTERNAL, legacyBridge, false));
  }

  private CouchbaseRequestTracer(CouchbaseTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public RequestSpan requestSpan(String name, RequestSpan parent) {
    Peer peer =
        TracingIdentifiers.SPAN_DISPATCH.equals(name)
                || "cb.dispatch_to_server".equals(name)
            ? CouchbaseRequestPeers.consume(parent)
            : null;
    CouchbaseSpan parentSpan = null;
    if (parent != null) {
      if (!(parent instanceof AgentRequestSpan)) {
        throw new IllegalArgumentException(
            "RequestSpan must be created by the OpenTelemetry agent");
      }
      parentSpan = ((AgentRequestSpan) parent).delegate;
    }
    return new AgentRequestSpan(name, tracer.startSpan(name, parentSpan), peer);
  }

  @Override
  public Mono<Void> start() {
    return Mono.empty();
  }

  @Override
  public Mono<Void> stop(Duration timeout) {
    return Mono.empty();
  }

  private static final class AgentRequestSpan implements RequestSpan {

    private final CouchbaseSpan delegate;
    private final CouchbaseSpanName spanName;
    private final boolean hasCapturedPeer;

    private AgentRequestSpan(String name, CouchbaseSpan delegate, @Nullable Peer peer) {
      this.delegate = delegate;
      this.spanName = new CouchbaseSpanName(name);
      this.hasCapturedPeer = peer != null;
      if (emitStableDatabaseSemconv() && peer != null) {
        delegate.setRawAttribute(NETWORK_PEER_ADDRESS.getKey(), peer.getAddress());
        delegate.setRawAttribute(NETWORK_PEER_PORT.getKey(), (long) peer.getPort());
      }
    }

    @Override
    public void setAttribute(String key, String value) {
      if (emitStableDatabaseSemconv()) {
        spanName.captureAttribute(key, value);
        if (!hasCapturedPeer && TracingIdentifiers.ATTR_REMOTE_HOSTNAME.equals(key)) {
          delegate.setRawAttribute(NETWORK_PEER_ADDRESS.getKey(), value);
        }
      }
      delegate.setAttribute(key, value);
    }

    @Override
    public void setAttribute(String key, boolean value) {
      delegate.setAttribute(key, value);
    }

    @Override
    public void setAttribute(String key, long value) {
      if (emitStableDatabaseSemconv()
          && !hasCapturedPeer
          && TracingIdentifiers.ATTR_REMOTE_PORT.equals(key)) {
        delegate.setRawAttribute(NETWORK_PEER_PORT.getKey(), value);
      }
      delegate.setAttribute(key, value);
    }

    @Override
    public void addEvent(String name, Instant timestamp) {
      delegate.addEvent(name, timestamp);
    }

    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod"})
    public void attribute(String key, String value) {
      if (emitStableDatabaseSemconv()) {
        spanName.captureAttribute(key, value);
      }
      delegate.setAttribute(key, value);
    }

    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod"})
    public void attribute(String key, boolean value) {
      delegate.setAttribute(key, value);
    }

    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod"})
    public void attribute(String key, long value) {
      delegate.setAttribute(key, value);
    }

    @SuppressWarnings({"EffectivelyPrivate", "UnusedMethod"})
    public void event(String name, Instant timestamp) {
      delegate.addEvent(name, timestamp);
    }

    @Override
    public void end() {
      if (spanName.isDatabaseRequest()) {
        delegate.updateName(spanName.spanName());
      }
      delegate.end();
    }

    @Override
    public void requestContext(RequestContext requestContext) {
      CouchbaseConfiguredTarget.capture(delegate, spanName, requestContext);
    }
  }
}
