/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.logs.EventBuilder;
import application.io.opentelemetry.api.logs.LogRecordBuilder;
import application.io.opentelemetry.api.logs.Severity;
import application.io.opentelemetry.context.Context;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

class ApplicationLogRecordBuilder implements EventBuilder {

  private final io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder;

  ApplicationLogRecordBuilder(io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder) {
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setEpoch(long l, TimeUnit timeUnit) {
    agentLogRecordBuilder.setEpoch(l, timeUnit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setEpoch(Instant instant) {
    agentLogRecordBuilder.setEpoch(instant);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setContext(Context context) {
    agentLogRecordBuilder.setContext(AgentContextStorage.getAgentContext(context));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setSeverity(Severity severity) {
    agentLogRecordBuilder.setSeverity(LogBridging.toAgent(severity));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setSeverityText(String s) {
    agentLogRecordBuilder.setSeverityText(s);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setBody(String s) {
    agentLogRecordBuilder.setBody(s);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public <T> LogRecordBuilder setAttribute(AttributeKey<T> attributeKey, T t) {
    @SuppressWarnings("unchecked")
    io.opentelemetry.api.common.AttributeKey<T> agentKey = Bridging.toAgent(attributeKey);
    if (agentKey != null) {
      agentLogRecordBuilder.setAttribute(agentKey, t);
    }
    return this;
  }

  @Override
  public void emit() {
    agentLogRecordBuilder.emit();
  }
}
