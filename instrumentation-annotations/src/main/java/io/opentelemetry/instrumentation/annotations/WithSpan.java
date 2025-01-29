/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation marks that an execution of this method or constructor should result in a new
 * {@link io.opentelemetry.api.trace.Span}.
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that a new span should be created whenever marked method is executed.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without some form of auto-instrumentation.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithSpan {
  /**
   * Optional name of the created span.
   *
   * <p>If not specified, an appropriate default name should be created by auto-instrumentation.
   * E.g. {@code "className"."method"}
   */
  String value() default "";

  /** Specify the {@link SpanKind} of span to be created. Defaults to {@link SpanKind#INTERNAL}. */
  SpanKind kind() default SpanKind.INTERNAL;

  /**
   * Specifies whether to use the current context as the parent when creating a Span.
   *
   * <p>If set to {@code true} (default), the created span will inherit the existing parent context,
   * forming part of the same trace.
   *
   * <p>If set to {@code false}, the created span will use {@link Context#root()} and have no
   * parent, starting a new trace independently.
   */
  boolean withParent() default true;
}
