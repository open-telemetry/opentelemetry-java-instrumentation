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
 * This annotation creates a {@link io.opentelemetry.api.metrics.LongCounter Counter} instrument
 * recording the number of invocations of the annotated method or constructor.
 *
 * <p>By default, the Counter instrument will have the following attributes:
 *
 * <ul>
 *   <li><b>code.namespace:</b> The fully qualified name of the class whose method is invoked.
 *   <li><b>code.function:</b> The name of the annotated method, or "new" if the annotation is on a
 *       constructor.
 *   <li><b>exception.type:</b> This is only present if an Exception is thrown, and contains the
 *       {@link Class#getCanonicalName() canonical name} of the Exception.
 * </ul>
 *
 * <p>Application developers can use this annotation to signal OpenTelemetry auto-instrumentation
 * that the Counter instrument should be created.
 *
 * <p>If you are a library developer, then probably you should NOT use this annotation, because it
 * is non-functional without the OpenTelemetry auto-instrumentation agent, or some other annotation
 * processor.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface Counted {

  /**
   * Name of the Counter instrument.
   *
   * <p>The name should follow the instrument naming rule: <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-naming-rule">https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-naming-rule</a>
   *
   * <p>The default name is {@code method.invocation.count}.
   */
  String value() default "";

  /**
   * Description of the instrument.
   *
   * <p>Description strings should follow the instrument description rules: <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description">https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-description</a>
   *
   * <p>This property would not take effect if the value is not specified.
   */
  String description() default "";

  /**
   * Unit of the instrument.
   *
   * <p>Unit strings should follow the instrument unit rules: <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-unit">https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#instrument-unit</a>
   *
   * <p>This property would not take effect if the value is not specified.
   */
  String unit() default "{invocation}";

  /**
   * List of key-value pairs to supply additional attributes.
   *
   * <p>Example:
   *
   * <pre>
   * {@literal @}Counted(
   *     additionalAttributes = {
   *       "key1", "value1",
   *       "key2", "value2",
   * })
   * </pre>
   */
  String[] additionalAttributes() default {};

  /**
   * Attribute name for the return value.
   *
   * <p>The name of the attribute for the return value of the method call. {@link Object#toString()}
   * will be called on the return value to convert it to a String.
   *
   * <p>By default, the instrument will not have an attribute with the return value.
   *
   * <p>Warning: be careful to fill it because it might cause an explosion of the cardinality on
   * your metric
   */
  String returnValueAttribute() default "";
}
