/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import javax.annotation.Nullable;

/** Object with a volatile field for storing application span. */
public interface ApplicationSpanHolder {

  /**
   * Read the application span.
   *
   * @return application span
   */
  @Nullable
  Object getApplicationSpan();

  /**
   * Set application span.
   *
   * @param value application span
   */
  void setApplicationSpan(Object value);
}
