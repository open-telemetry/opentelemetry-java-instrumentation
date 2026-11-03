/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;

/** A builder that exposes methods for adding links to a span. */
public interface SpanLinksBuilder {

  /**
   * Adds a link to the newly created {@code Span}. Link recording is delegated to the underlying
   * {@link SpanBuilder}.
   *
   * @param spanContext the context of the linked {@code Span}.
   * @return this.
   * @see SpanBuilder#addLink(SpanContext)
   */
  @CanIgnoreReturnValue
  SpanLinksBuilder addLink(SpanContext spanContext);

  /**
   * Adds a link to the newly created {@code Span}. Link recording is delegated to the underlying
   * {@link SpanBuilder}.
   *
   * @param spanContext the context of the linked {@code Span}.
   * @param attributes the attributes of the {@code Link}.
   * @return this.
   * @see SpanBuilder#addLink(SpanContext, Attributes)
   */
  @CanIgnoreReturnValue
  SpanLinksBuilder addLink(SpanContext spanContext, Attributes attributes);
}
