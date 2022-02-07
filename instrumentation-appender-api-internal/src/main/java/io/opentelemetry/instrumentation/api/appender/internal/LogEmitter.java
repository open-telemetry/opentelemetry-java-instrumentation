/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@link LogEmitter} is the entry point into a log pipeline.
 *
 * <p>Obtain a log builder via {@link #logBuilder()}, add properties using the setters, and emit it
 * via {@link LogBuilder#emit()}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ThreadSafe
public interface LogEmitter {

  /** Return a new {@link LogBuilder} to emit a log. */
  LogBuilder logBuilder();
}
