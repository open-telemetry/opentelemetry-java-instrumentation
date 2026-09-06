/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

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

  public CouchbaseTracer(
      Tracer tracer, boolean inheritCurrentContext, SpanKind spanKind, boolean makeCurrentOnEnd) {
    this.tracer = tracer;
    this.inheritCurrentContext = inheritCurrentContext;
    this.spanKind = spanKind;
    this.makeCurrentOnEnd = makeCurrentOnEnd;
  }

  public CouchbaseSpan startSpan(String name, @Nullable CouchbaseSpan parent) {
    SpanBuilder spanBuilder = tracer.spanBuilder(name).setSpanKind(spanKind);
    if (parent != null) {
      spanBuilder.setParent(Context.current().with(parent.getSpan()));
    } else if (inheritCurrentContext) {
      spanBuilder.setParent(Context.current());
    } else {
      spanBuilder.setNoParent();
    }
    return new CouchbaseSpan(spanBuilder.startSpan(), makeCurrentOnEnd);
  }
}
