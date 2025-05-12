/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.common.Value;
import application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import application.io.opentelemetry.api.incubator.common.ExtendedAttributes;
import application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import application.io.opentelemetry.api.logs.Severity;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLogRecordBuilder150;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

public class ApplicationLogRecordBuilder150Incubator extends ApplicationLogRecordBuilder150
    implements ExtendedLogRecordBuilder {

  private final io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder agentLogRecordBuilder;

  ApplicationLogRecordBuilder150Incubator(
      io.opentelemetry.api.logs.LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder =
        (io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder) agentLogRecordBuilder;
  }

  @Override
  public ExtendedLogRecordBuilder setEventName(String eventName) {
    agentLogRecordBuilder.setEventName(eventName);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setTimestamp(long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setTimestamp(Instant instant) {
    agentLogRecordBuilder.setTimestamp(instant);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setObservedTimestamp(long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setObservedTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setObservedTimestamp(Instant instant) {
    agentLogRecordBuilder.setObservedTimestamp(instant);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setContext(Context applicationContext) {
    agentLogRecordBuilder.setContext(AgentContextStorage.getAgentContext(applicationContext));
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setSeverity(Severity severity) {
    agentLogRecordBuilder.setSeverity(convertSeverity(severity));
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setSeverityText(String severityText) {
    agentLogRecordBuilder.setSeverityText(severityText);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setBody(String body) {
    agentLogRecordBuilder.setBody(body);
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setBody(Value<?> body) {
    agentLogRecordBuilder.setBody(convertValue(body));
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setAllAttributes(Attributes applicationAttributes) {
    agentLogRecordBuilder.setAllAttributes(Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setAllAttributes(ExtendedAttributes attributes) {
    return this;
  }

  @Override
  public <T> ExtendedLogRecordBuilder setAttribute(AttributeKey<T> key, @Nullable T value) {
    return (ExtendedLogRecordBuilder) super.setAttribute(key, value);
  }

  @Override
  public <T> ExtendedLogRecordBuilder setAttribute(ExtendedAttributeKey<T> key, T value) {
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setException(Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }

  private static io.opentelemetry.api.logs.Severity convertSeverity(Severity applicationSeverity) {
    if (applicationSeverity == null) {
      return null;
    }
    switch (applicationSeverity) {
      case UNDEFINED_SEVERITY_NUMBER:
        return io.opentelemetry.api.logs.Severity.UNDEFINED_SEVERITY_NUMBER;
      case TRACE:
        return io.opentelemetry.api.logs.Severity.TRACE;
      case TRACE2:
        return io.opentelemetry.api.logs.Severity.TRACE2;
      case TRACE3:
        return io.opentelemetry.api.logs.Severity.TRACE3;
      case TRACE4:
        return io.opentelemetry.api.logs.Severity.TRACE4;
      case DEBUG:
        return io.opentelemetry.api.logs.Severity.DEBUG;
      case DEBUG2:
        return io.opentelemetry.api.logs.Severity.DEBUG2;
      case DEBUG3:
        return io.opentelemetry.api.logs.Severity.DEBUG3;
      case DEBUG4:
        return io.opentelemetry.api.logs.Severity.DEBUG4;
      case INFO:
        return io.opentelemetry.api.logs.Severity.INFO;
      case INFO2:
        return io.opentelemetry.api.logs.Severity.INFO2;
      case INFO3:
        return io.opentelemetry.api.logs.Severity.INFO3;
      case INFO4:
        return io.opentelemetry.api.logs.Severity.INFO4;
      case WARN:
        return io.opentelemetry.api.logs.Severity.WARN;
      case WARN2:
        return io.opentelemetry.api.logs.Severity.WARN2;
      case WARN3:
        return io.opentelemetry.api.logs.Severity.WARN3;
      case WARN4:
        return io.opentelemetry.api.logs.Severity.WARN4;
      case ERROR:
        return io.opentelemetry.api.logs.Severity.ERROR;
      case ERROR2:
        return io.opentelemetry.api.logs.Severity.ERROR2;
      case ERROR3:
        return io.opentelemetry.api.logs.Severity.ERROR3;
      case ERROR4:
        return io.opentelemetry.api.logs.Severity.ERROR4;
      case FATAL:
        return io.opentelemetry.api.logs.Severity.FATAL;
      case FATAL2:
        return io.opentelemetry.api.logs.Severity.FATAL2;
      case FATAL3:
        return io.opentelemetry.api.logs.Severity.FATAL3;
      case FATAL4:
        return io.opentelemetry.api.logs.Severity.FATAL4;
    }

    throw new IllegalStateException("Unhandled severity: " + applicationSeverity.name());
  }
}
