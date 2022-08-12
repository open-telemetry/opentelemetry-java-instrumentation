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
 * <p>Obtain a {@link LogRecordBuilder} via {@link LogEmitter#logBuilder()}, add properties using
 * the setters, and emit the log by calling {@link #emit()}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface LogRecordBuilder {

  /** Set the epoch timestamp using the timestamp and unit. */
  LogRecordBuilder setEpoch(long timestamp, TimeUnit unit);

  /** Set the epoch timestamp using the instant. */
  LogRecordBuilder setEpoch(Instant instant);

  /** Set the context. */
  LogRecordBuilder setContext(Context context);

  /** Set the severity. */
  LogRecordBuilder setSeverity(Severity severity);

  /** Set the severity text. */
  LogRecordBuilder setSeverityText(String severityText);

  /** Set the body string. */
  LogRecordBuilder setBody(String body);

  /** Set the attributes. */
  LogRecordBuilder setAllAttributes(Attributes attributes);

  /** Emit the log. */
  void emit();
}
