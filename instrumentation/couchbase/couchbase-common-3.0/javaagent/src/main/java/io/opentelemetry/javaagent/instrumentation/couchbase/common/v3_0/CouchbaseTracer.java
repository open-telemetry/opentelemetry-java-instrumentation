/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class CouchbaseTracer {

  private final Tracer tracer;
  private final boolean inheritCurrentContext;
  private final SpanKind spanKind;
  private final boolean makeCurrentOnEnd;
  private final boolean mapLegacyNetworkPeerAttributes;

  public CouchbaseTracer(
      Tracer tracer, boolean inheritCurrentContext, SpanKind spanKind, boolean makeCurrentOnEnd) {
    this(tracer, inheritCurrentContext, spanKind, makeCurrentOnEnd, true);
  }

  public CouchbaseTracer(
      Tracer tracer,
      boolean inheritCurrentContext,
      SpanKind spanKind,
      boolean makeCurrentOnEnd,
      boolean mapLegacyNetworkPeerAttributes) {
    this.tracer = tracer;
    this.inheritCurrentContext = inheritCurrentContext;
    this.spanKind = spanKind;
    this.makeCurrentOnEnd = makeCurrentOnEnd;
    this.mapLegacyNetworkPeerAttributes = mapLegacyNetworkPeerAttributes;
  }

  public CouchbaseSpan startSpan(String name, @Nullable CouchbaseSpan parent) {
    boolean sdkDetailSpan = isSdkDetailSpan(name);
    if (v3Preview() && sdkDetailSpan && !CouchbaseSpan.emitExperimentalTelemetry()) {
      return new CouchbaseSpan(Span.getInvalid(), makeCurrentOnEnd, mapLegacyNetworkPeerAttributes);
    }

    SpanKind effectiveSpanKind = v3Preview() ? (sdkDetailSpan ? INTERNAL : CLIENT) : spanKind;
    SpanBuilder spanBuilder = tracer.spanBuilder(name).setSpanKind(effectiveSpanKind);
    if (parent != null) {
      spanBuilder.setParent(Context.current().with(parent.getSpan()));
    } else if (inheritCurrentContext) {
      spanBuilder.setParent(Context.current());
    } else {
      spanBuilder.setNoParent();
    }
    return new CouchbaseSpan(
        spanBuilder.startSpan(), makeCurrentOnEnd, mapLegacyNetworkPeerAttributes);
  }

  private static boolean isSdkDetailSpan(String name) {
    return "request_encoding".equals(name)
        || "cb.request_encoding".equals(name)
        || "dispatch_to_server".equals(name)
        || "cb.dispatch_to_server".equals(name);
  }
}
