/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.ValueBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ApplicationLogRecordBuilder
    implements application.io.opentelemetry.api.logs.LogRecordBuilder {

  private final LogRecordBuilder agentLogRecordBuilder;

  protected ApplicationLogRecordBuilder(LogRecordBuilder agentLogRecordBuilder) {
    this.agentLogRecordBuilder = agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setTimestamp(
      long l, TimeUnit timeUnit) {
    agentLogRecordBuilder.setTimestamp(l, timeUnit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setTimestamp(Instant instant) {
    agentLogRecordBuilder.setTimestamp(instant);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setObservedTimestamp(
      long l, TimeUnit timeUnit) {
    agentLogRecordBuilder.setObservedTimestamp(l, timeUnit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setObservedTimestamp(
      Instant instant) {
    agentLogRecordBuilder.setObservedTimestamp(instant);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setContext(
      application.io.opentelemetry.context.Context context) {
    agentLogRecordBuilder.setContext(AgentContextStorage.getAgentContext(context));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setSeverity(
      application.io.opentelemetry.api.logs.Severity severity) {
    agentLogRecordBuilder.setSeverity(LogBridging.toAgent(severity));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setSeverityText(String s) {
    agentLogRecordBuilder.setSeverityText(s);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LogRecordBuilder setBody(String s) {
    agentLogRecordBuilder.setBody(s);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  // unchecked: toAgent returns raw AttributeKey, VALUE bridging requires casting to Object key
  @SuppressWarnings({"unchecked", "rawtypes"})
  public <T> application.io.opentelemetry.api.logs.LogRecordBuilder setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> attributeKey, T value) {
    @SuppressWarnings("unchecked") // toAgent uses raw AttributeKey
    AttributeKey<T> agentKey = Bridging.toAgent(attributeKey);
    if (agentKey != null) {
      // For VALUE type attributes, need to bridge the Value object as well
      if (attributeKey.getType().name().equals("VALUE")) {
        agentLogRecordBuilder.setAttribute((AttributeKey) agentKey, ValueBridging.toAgent(value));
      } else {
        agentLogRecordBuilder.setAttribute(agentKey, value);
      }
    }
    return this;
  }

  @Override
  public void emit() {
    agentLogRecordBuilder.emit();
  }
}
