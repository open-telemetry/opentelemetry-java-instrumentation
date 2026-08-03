/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation marks a method whose invocations should be counted by an OpenTelemetry {@link
 * io.opentelemetry.api.metrics.LongCounter}.
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that the counter should be incremented whenever the marked method is invoked.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without some form of auto-instrumentation.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {
  /**
   * Optional name of the metric.
   *
   * <p>If not specified, an appropriate default name should be created by auto-instrumentation.
   * E.g. {@code "className"."method"}
   */
  String value() default "";
}
