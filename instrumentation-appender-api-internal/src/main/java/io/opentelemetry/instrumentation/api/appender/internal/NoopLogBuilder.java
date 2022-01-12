/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class NoopLogBuilder implements LogBuilder {

  static final LogBuilder INSTANCE = new NoopLogBuilder();

  @Override
  public LogBuilder setEpoch(long timestamp, TimeUnit unit) {
    return this;
  }

  @Override
  public LogBuilder setEpoch(Instant instant) {
    return this;
  }

  @Override
  public LogBuilder setContext(Context context) {
    return this;
  }

  @Override
  public LogBuilder setSeverity(Severity severity) {
    return this;
  }

  @Override
  public LogBuilder setSeverityText(String severityText) {
    return this;
  }

  @Override
  public LogBuilder setName(String name) {
    return this;
  }

  @Override
  public LogBuilder setBody(String body) {
    return this;
  }

  @Override
  public LogBuilder setAttributes(Attributes attributes) {
    return this;
  }

  @Override
  public void emit() {}
}
