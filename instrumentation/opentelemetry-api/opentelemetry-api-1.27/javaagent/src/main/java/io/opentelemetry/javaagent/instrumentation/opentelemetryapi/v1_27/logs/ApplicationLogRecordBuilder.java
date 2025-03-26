/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.logs.LogRecordBuilder;
import application.io.opentelemetry.api.logs.Severity;
import application.io.opentelemetry.context.Context;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ApplicationLogRecordBuilder implements LogRecordBuilder {

  private final io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder;

  protected ApplicationLogRecordBuilder(
      io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder) {
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setTimestamp(long l, TimeUnit timeUnit) {
    agentLogRecordBuilder.setTimestamp(l, timeUnit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogRecordBuilder setTimestamp(Instant instant) {
    agentLogRecordBuilder.setTimestamp(instant);
    return this;
  }

  @Override
  public LogRecordBuilder setObservedTimestamp(long l, TimeUnit timeUnit) {
    agentLogRecordBuilder.setObservedTimestamp(l, timeUnit);
    return this;
  }

  @Override
  public LogRecordBuilder setObservedTimestamp(Instant instant) {
    agentLogRecordBuilder.setObservedTimestamp(instant);
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
