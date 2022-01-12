/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sdk.appender.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class DelegatingLogBuilder implements LogBuilder {

  private final io.opentelemetry.sdk.logs.LogBuilder delegate;

  DelegatingLogBuilder(io.opentelemetry.sdk.logs.LogBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogBuilder setEpoch(long timestamp, TimeUnit unit) {
    delegate.setEpoch(timestamp, unit);
    return this;
  }

  @Override
  public LogBuilder setEpoch(Instant instant) {
    delegate.setEpoch(instant);
    return this;
  }

  @Override
  public LogBuilder setContext(Context context) {
    delegate.setContext(context);
    return this;
  }

  @Override
  public LogBuilder setSeverity(Severity severity) {
    delegate.setSeverity(io.opentelemetry.sdk.logs.data.Severity.valueOf(severity.name()));
    return this;
  }

  @Override
  public LogBuilder setSeverityText(String severityText) {
    delegate.setSeverityText(severityText);
    return this;
  }

  @Override
  public LogBuilder setName(String name) {
    delegate.setName(name);
    return this;
  }

  @Override
  public LogBuilder setBody(String body) {
    delegate.setBody(body);
    return this;
  }

  @Override
  public LogBuilder setAttributes(Attributes attributes) {
    delegate.setAttributes(attributes);
    return this;
  }

  @Override
  public void emit() {
    delegate.emit();
  }
}
