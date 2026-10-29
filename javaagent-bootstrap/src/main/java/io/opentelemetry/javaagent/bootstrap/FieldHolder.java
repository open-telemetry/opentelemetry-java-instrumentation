/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/**
 * Object with an extra volatile field that instrumentation can use to associate state with given
 * object.
 */
public interface FieldHolder {

  /**
   * Read the field value.
   *
   * @return field value
   */
  Object getFieldValue();

  /**
   * Set the field value.
   *
   * @param value field value
   */
  void setFieldValue(Object value);
}
