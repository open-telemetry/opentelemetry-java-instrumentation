/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sdk.appender.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogRecordBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class DelegatingLogRecordBuilder implements LogRecordBuilder {

  private final io.opentelemetry.sdk.logs.LogRecordBuilder delegate;

  DelegatingLogRecordBuilder(io.opentelemetry.sdk.logs.LogRecordBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogRecordBuilder setEpoch(long timestamp, TimeUnit unit) {
    delegate.setEpoch(timestamp, unit);
    return this;
  }

  @Override
  public LogRecordBuilder setEpoch(Instant instant) {
    delegate.setEpoch(instant);
    return this;
  }

  @Override
  public LogRecordBuilder setContext(Context context) {
    delegate.setContext(context);
    return this;
  }

  @Override
  public LogRecordBuilder setSeverity(Severity severity) {
    delegate.setSeverity(io.opentelemetry.sdk.logs.data.Severity.valueOf(severity.name()));
    return this;
  }

  @Override
  public LogRecordBuilder setSeverityText(String severityText) {
    delegate.setSeverityText(severityText);
    return this;
  }

  @Override
  public LogRecordBuilder setBody(String body) {
    delegate.setBody(body);
    return this;
  }

  @Override
  public LogRecordBuilder setAllAttributes(Attributes attributes) {
    delegate.setAllAttributes(attributes);
    return this;
  }

  @Override
  public void emit() {
    delegate.emit();
  }
}
