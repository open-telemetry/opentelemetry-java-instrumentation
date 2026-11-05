/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class CouchbaseTracer {

  private final Tracer tracer;
  private final Logger exceptionLogger;
  private final boolean inheritCurrentContext;
  private final boolean makeCurrentOnEnd;
  private final boolean mapLegacyNetworkPeerAttributes;

  public CouchbaseTracer(
      Tracer tracer,
      Logger exceptionLogger,
      boolean inheritCurrentContext,
      boolean makeCurrentOnEnd,
      boolean mapLegacyNetworkPeerAttributes) {
    this.tracer = tracer;
    this.exceptionLogger = exceptionLogger;
    this.inheritCurrentContext = inheritCurrentContext;
    this.makeCurrentOnEnd = makeCurrentOnEnd;
    this.mapLegacyNetworkPeerAttributes = mapLegacyNetworkPeerAttributes;
  }

  public CouchbaseSpan startSpan(String name, @Nullable CouchbaseSpan parent) {
    boolean sdkDetailSpan = isKnownSdkDetailSpan(name);
    if (sdkDetailSpan && !CouchbaseSpan.emitExperimentalTelemetry()) {
      return new CouchbaseSpan(
          Span.getInvalid(), exceptionLogger, makeCurrentOnEnd, mapLegacyNetworkPeerAttributes);
    }

    SpanBuilder spanBuilder =
        tracer.spanBuilder(name).setSpanKind(sdkDetailSpan ? INTERNAL : CLIENT);
    if (parent != null) {
      spanBuilder.setParent(Context.current().with(parent.getSpan()));
    } else if (inheritCurrentContext) {
      spanBuilder.setParent(Context.current());
    } else {
      spanBuilder.setNoParent();
    }
    return new CouchbaseSpan(
        spanBuilder.startSpan(), exceptionLogger, makeCurrentOnEnd, mapLegacyNetworkPeerAttributes);
  }

  // The Couchbase tracing API does not identify SDK detail spans separately. Add new SDK detail
  // span names here; unknown names are emitted to avoid dropping new database operation spans.
  private static boolean isKnownSdkDetailSpan(String name) {
    return "request_encoding".equals(name)
        || "cb.request_encoding".equals(name)
        || "dispatch_to_server".equals(name)
        || "cb.dispatch_to_server".equals(name);
  }
}
