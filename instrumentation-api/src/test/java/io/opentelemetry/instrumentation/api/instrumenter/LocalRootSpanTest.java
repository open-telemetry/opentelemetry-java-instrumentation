/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(LocalRootSpan.isLocalRoot(parentContext)).isTrue();
  }

  @Test
  void spanWithInvalidParentIsLocalRoot() {
    Context parentContext = Context.root().with(Span.getInvalid());
    assertThat(LocalRootSpan.isLocalRoot(parentContext)).isTrue();
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
    assertThat(LocalRootSpan.isLocalRoot(parentContext)).isTrue();
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
    assertThat(LocalRootSpan.isLocalRoot(parentContext)).isFalse();
  }

  @Test
  void shouldGetLocalRootSpan() {
    Span span = Span.getInvalid();
    Context context = LocalRootSpan.store(Context.root(), span);

    try (Scope ignored = context.makeCurrent()) {
      assertThat(LocalRootSpan.current()).isSameAs(span);
    }
    assertThat(LocalRootSpan.fromContext(context)).isSameAs(span);
    assertThat(LocalRootSpan.fromContextOrNull(context)).isSameAs(span);
  }

  @Test
  void shouldNotGetLocalRootSpanIfThereIsNone() {
    Context context = Context.root();

    try (Scope ignored = context.makeCurrent()) {
      assertThat(LocalRootSpan.current().getSpanContext().isValid()).isFalse();
    }
    assertThat(LocalRootSpan.fromContext(context).getSpanContext().isValid()).isFalse();
    assertThat(LocalRootSpan.fromContextOrNull(context)).isNull();
  }
}
