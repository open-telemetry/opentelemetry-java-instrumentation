/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class TracingBuilder<
    REQUEST, RESPONSE, T extends TracingBuilder<REQUEST, RESPONSE, T>> {

  final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>>
      additionalAttributesExtractors = new ArrayList<>();
  final List<ContextCustomizer<? super REQUEST>> additionalContextCustomizers = new ArrayList<>();
  final List<RequestListener> additionalListeners = new ArrayList<>();
  final List<SpanLinksExtractor<? super REQUEST>> additionalLinksExtractors = new ArrayList<>();

  Function<? super ErrorCauseExtractor, ? extends ErrorCauseExtractor>
      transformErrorCauseExtractor = Function.identity();

  Function<? super SpanNameExtractor<? super REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
      transformSpanNameExtractor = Function.identity();

  Function<
          ? super SpanStatusExtractor<? super REQUEST, ? super RESPONSE>,
          ? extends SpanStatusExtractor<? super REQUEST, ? super RESPONSE>>
      transformSpanStatusExtractor = Function.identity();

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors of this
   * instrumentation.
   */
  public T addAttributesExtractor(
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    requireNonNull(attributesExtractor, "attributesExtractor");
    additionalAttributesExtractors.add(attributesExtractor);
    return self();
  }

  /**
   * Adds an additional {@link ContextCustomizer} to invoke to customizer the {@link
   * io.opentelemetry.context.Context} created for an instrumented operation. The {@link
   * ContextCustomizer} will be executed after all default customizers of this instrumentation.
   */
  public T addContextCustomizer(ContextCustomizer<? super REQUEST> contextCustomizer) {
    requireNonNull(contextCustomizer, "contextCustomizer");
    additionalContextCustomizers.add(contextCustomizer);
    return self();
  }

  /**
   * Adds an additional {@link SpanLinksExtractor} to invoke to add links to a created span. The
   * {@link SpanLinksExtractor} will be executed after all default extractors of this
   * instrumentation.
   */
  public T addLinksExtractor(SpanLinksExtractor<? super REQUEST> linksExtractor) {
    requireNonNull(linksExtractor, "linksExtractor");
    additionalLinksExtractors.add(linksExtractor);
    return self();
  }

  /**
   * Adds an additional {@link RequestListener} to invoke when an instrumented operation, for
   * example a request, is started and ended. The {@link RequestListener} will be executed after all
   * default executors of this instrumentation.
   */
  public T addRequestListener(RequestListener listener) {
    requireNonNull(listener, "listener");
    additionalListeners.add(listener);
    return self();
  }

  /**
   * Sets a {@link Function} to return an {@link ErrorCauseExtractor} to use to determine the cause
   * of an exception thrown during the execution of an instrumented operation. The {@link Function}
   * will be provided the instrumentation's default {@link ErrorCauseExtractor} which can be used to
   * delegate to the default behavior where needed.
   */
  public T transformErrorCauseExtractor(
      Function<? super ErrorCauseExtractor, ? extends ErrorCauseExtractor>
          transformErrorCauseExtractor) {
    requireNonNull(transformErrorCauseExtractor, "transformErrorCauseExtractor");
    this.transformErrorCauseExtractor = transformErrorCauseExtractor;
    return self();
  }

  /**
   * Sets a {@link Function} to return a {@link SpanNameExtractor} to use to determine the name of a
   * new span. The {@link Function} will be provided the instrumentation's default {@link
   * SpanNameExtractor} which can be used to delegate to the default behavior where needed.
   */
  public T transformSpanNameExtractor(
      Function<
              ? super SpanNameExtractor<? super REQUEST>,
              ? extends SpanNameExtractor<? super REQUEST>>
          transformSpanNameExtractor) {
    requireNonNull(transformSpanNameExtractor, "transformSpanNameExtractor");
    this.transformSpanNameExtractor = transformSpanNameExtractor;
    return self();
  }

  /**
   * Sets a {@link Function} to return a {@link SpanNameExtractor} to use to determine the name of a
   * new span. The {@link Function} will be provided the instrumentation's default {@link
   * SpanNameExtractor} which can be used to delegate to the default behavior where needed.
   */
  public T transformSpanStatusExtractor(
      Function<
              ? super SpanStatusExtractor<? super REQUEST, ? super RESPONSE>,
              ? extends SpanStatusExtractor<? super REQUEST, ? super RESPONSE>>
          transformSpanStatusExtractor) {
    requireNonNull(transformSpanStatusExtractor, "transformSpanStatusExtractor");
    this.transformSpanStatusExtractor = transformSpanStatusExtractor;
    return self();
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }
}
