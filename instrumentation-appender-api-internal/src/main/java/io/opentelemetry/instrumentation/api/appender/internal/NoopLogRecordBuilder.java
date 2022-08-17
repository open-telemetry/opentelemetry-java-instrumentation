/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class NoopLogRecordBuilder implements LogRecordBuilder {

  static final LogRecordBuilder INSTANCE = new NoopLogRecordBuilder();

  @Override
  public LogRecordBuilder setEpoch(long timestamp, TimeUnit unit) {
    return this;
  }

  @Override
  public LogRecordBuilder setEpoch(Instant instant) {
    return this;
  }

  @Override
  public LogRecordBuilder setContext(Context context) {
    return this;
  }

  @Override
  public LogRecordBuilder setSeverity(Severity severity) {
    return this;
  }

  @Override
  public LogRecordBuilder setSeverityText(String severityText) {
    return this;
  }

  @Override
  public LogRecordBuilder setBody(String body) {
    return this;
  }

  @Override
  public LogRecordBuilder setAllAttributes(Attributes attributes) {
    return this;
  }

  @Override
  public void emit() {}
}
