/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Used to construct and emit logs from a {@link LogEmitter}.
 *
 * <p>Obtain a {@link LogBuilder} via {@link LogEmitter#logBuilder()}, add properties using the
 * setters, and emit the log by calling {@link #emit()}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface LogBuilder {

  /** Set the epoch timestamp using the timestamp and unit. */
  LogBuilder setEpoch(long timestamp, TimeUnit unit);

  /** Set the epoch timestamp using the instant. */
  LogBuilder setEpoch(Instant instant);

  /** Set the context. */
  LogBuilder setContext(Context context);

  /** Set the severity. */
  LogBuilder setSeverity(Severity severity);

  /** Set the severity text. */
  LogBuilder setSeverityText(String severityText);

  /** Set the name. */
  LogBuilder setName(String name);

  /** Set the body string. */
  LogBuilder setBody(String body);

  /** Set the attributes. */
  LogBuilder setAttributes(Attributes attributes);

  /** Emit the log. */
  void emit();
}
