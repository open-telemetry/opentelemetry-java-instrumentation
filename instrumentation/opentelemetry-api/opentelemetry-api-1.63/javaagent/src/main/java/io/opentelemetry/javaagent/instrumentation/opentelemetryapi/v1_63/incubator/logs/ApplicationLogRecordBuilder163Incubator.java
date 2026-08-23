/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.incubator.logs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.logs.ApplicationLogRecordBuilder163;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public class ApplicationLogRecordBuilder163Incubator extends ApplicationLogRecordBuilder163
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder {

  private final ExtendedLogRecordBuilder agentLogRecordBuilder;

  ApplicationLogRecordBuilder163Incubator(LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = (ExtendedLogRecordBuilder) agentLogRecordBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setEventName(
      String eventName) {
    agentLogRecordBuilder.setEventName(eventName);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setTimestamp(
      long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setTimestamp(timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setTimestamp(
      Instant instant) {
    agentLogRecordBuilder.setTimestamp(instant);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      setObservedTimestamp(long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setObservedTimestamp(timestamp, unit);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      setObservedTimestamp(Instant instant) {
    agentLogRecordBuilder.setObservedTimestamp(instant);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setContext(
      application.io.opentelemetry.context.Context applicationContext) {
    agentLogRecordBuilder.setContext(AgentContextStorage.getAgentContext(applicationContext));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setSeverity(
      application.io.opentelemetry.api.logs.Severity severity) {
    agentLogRecordBuilder.setSeverity(LogBridging.toAgent(severity));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setSeverityText(
      String severityText) {
    agentLogRecordBuilder.setSeverityText(severityText);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setBody(
      String body) {
    agentLogRecordBuilder.setBody(body);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setBody(
      @Nullable application.io.opentelemetry.api.common.Value<?> body) {
    agentLogRecordBuilder.setBody(convertValue(body));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAllAttributes(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentLogRecordBuilder.setAllAttributes(Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public <T> application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> key, @Nullable T value) {
    return (application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setException(
      Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }
}
