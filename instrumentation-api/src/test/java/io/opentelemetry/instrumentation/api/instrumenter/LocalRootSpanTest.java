/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class LocalRootSpanTest {

  @Test
  void spanWithoutParentIsLocalRoot() {
    Context parentContext = Context.root();
    assertTrue(LocalRootSpan.isLocalRoot(parentContext));
  }

  @Test
  void spanWithInvalidParentIsLocalRoot() {
    Context parentContext = Context.root().with(Span.getInvalid());
    assertTrue(LocalRootSpan.isLocalRoot(parentContext));
  }

  @Test
  void spanWithRemoteParentIsLocalRoot() {
    Context parentContext =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.createFromRemoteParent(
                        "00000000000000000000000000000001",
                        "0000000000000002",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));
    assertTrue(LocalRootSpan.isLocalRoot(parentContext));
  }

  @Test
  void spanWithValidLocalParentIsNotLocalRoot() {
    Context parentContext =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "00000000000000000000000000000001",
                        "0000000000000002",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));
    assertFalse(LocalRootSpan.isLocalRoot(parentContext));
  }

  @Test
  void shouldGetLocalRootSpan() {
    Span span = Span.getInvalid();
    Context context = LocalRootSpan.store(Context.root(), span);

    try (Scope ignored = context.makeCurrent()) {
      assertSame(span, LocalRootSpan.current());
    }
    assertSame(span, LocalRootSpan.fromContext(context));
    assertSame(span, LocalRootSpan.fromContextOrNull(context));
  }

  @Test
  void shouldNotGetLocalRootSpanIfThereIsNone() {
    Context context = Context.root();

    try (Scope ignored = context.makeCurrent()) {
      assertFalse(LocalRootSpan.current().getSpanContext().isValid());
    }
    assertFalse(LocalRootSpan.fromContext(context).getSpanContext().isValid());
    assertNull(LocalRootSpan.fromContextOrNull(context));
  }
}
