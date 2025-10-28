/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.annotations.incubator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation creates a {@link io.opentelemetry.api.metrics.DoubleHistogram Histogram}
 * instrument observing the duration of invocations of the annotated method or constructor.
 *
 * <p>By default, the Histogram instrument will have the following attributes:
 *
 * <ul>
 *   <li><b>code.function.name:</b> The fully qualified name of the class whose method is invoked.
 *   <li><b>error.type:</b> This is only present if an Exception is thrown, and contains the {@link
 *       Class#getName name} of the Exception class.
 * </ul>
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that the Histogram instrument should be created.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without the OpenTelemetry auto-instrumentation agent, or some other annotation
 * processor.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {

  /**
   * Name of the Histogram metric.
   *
   * <p>The name should follow the metric naming rules: <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-naming-rule">https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-naming-rule</a>
   */
  String name();

  /**
   * Description for the metric.
   *
   * <p>Description strings should follow the metric description rules: <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description">https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description</a>
   */
  String description() default "";
}
