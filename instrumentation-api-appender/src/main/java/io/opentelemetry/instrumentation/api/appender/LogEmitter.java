/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link LogEmitter} is the entry point into a log pipeline.
 *
 * <p>Obtain a log builder via {@link #logBuilder()}, add properties using the setters, and emit it
 * via {@link LogBuilder#emit()}.
 */
@ThreadSafe
public interface LogEmitter {

  /** Return a {@link LogBuilder} to emit a log. */
  LogBuilder logBuilder();
}
