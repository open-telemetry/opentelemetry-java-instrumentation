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
 * This annotation marks that an execution of this method should result in a new {@link
 * io.opentelemetry.api.trace.Span}.
 *
 * <p>Using this annotation on constructors is not supported by OpenTelemetry instrumentation. The
 * {@link ElementType#CONSTRUCTOR} target exists only for backward compatibility and is planned to
 * be removed in the 3.0 release.
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that a new span should be created whenever the marked method is executed.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without some form of auto-instrumentation.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR}) // CONSTRUCTOR target to be removed in 3.0
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
   * Specifies whether to inherit the current context when creating a span.
   *
   * <p>If set to {@code true} (default), the created span will use the current context as its
   * parent, remaining within the same trace.
   *
   * <p>If set to {@code false}, the created span will use {@link Context#root()} as its parent,
   * starting a new, independent trace.
   */
  boolean inheritContext() default true;
}
