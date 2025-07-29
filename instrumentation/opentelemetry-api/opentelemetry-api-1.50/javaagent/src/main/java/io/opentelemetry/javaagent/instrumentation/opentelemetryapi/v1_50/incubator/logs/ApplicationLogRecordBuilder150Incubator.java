/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs;

import static application.io.opentelemetry.api.incubator.common.ExtendedAttributeType.EXTENDED_ATTRIBUTES;
import static java.util.logging.Level.FINE;

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
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLogRecordBuilder150;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class ApplicationLogRecordBuilder150Incubator extends ApplicationLogRecordBuilder150
    implements ExtendedLogRecordBuilder {
  private static final Logger logger =
      Logger.getLogger(ApplicationLogRecordBuilder150Incubator.class.getName());

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
    agentLogRecordBuilder.setSeverity(LogBridging.toAgent(severity));
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
    agentLogRecordBuilder.setAllAttributes(convertExtendedAttributes(attributes));
    return this;
  }

  @Override
  public <T> ExtendedLogRecordBuilder setAttribute(AttributeKey<T> key, @Nullable T value) {
    return (ExtendedLogRecordBuilder) super.setAttribute(key, value);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> ExtendedLogRecordBuilder setAttribute(ExtendedAttributeKey<T> key, T value) {
    io.opentelemetry.api.incubator.common.ExtendedAttributeKey<T> agentKey =
        convertExtendedAttributeKey(key);
    if (agentKey != null) {
      if (key.getType() == EXTENDED_ATTRIBUTES) {
        value = (T) convertExtendedAttributes((ExtendedAttributes) value);
      }
      agentLogRecordBuilder.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  public ExtendedLogRecordBuilder setException(Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static io.opentelemetry.api.incubator.common.ExtendedAttributes convertExtendedAttributes(
      ExtendedAttributes applicationAttributes) {
    io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder agentAttributes =
        io.opentelemetry.api.incubator.common.ExtendedAttributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          io.opentelemetry.api.incubator.common.ExtendedAttributeKey agentKey =
              convertExtendedAttributeKey(key);
          if (agentKey != null) {
            if (key.getType() == EXTENDED_ATTRIBUTES) {
              value = convertExtendedAttributes((ExtendedAttributes) value);
            }
            agentAttributes.put(agentKey, value);
          }
        });
    return agentAttributes.build();
  }

  @SuppressWarnings({"rawtypes"})
  private static io.opentelemetry.api.incubator.common.ExtendedAttributeKey
      convertExtendedAttributeKey(ExtendedAttributeKey applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.stringKey(
            applicationKey.getKey());
      case BOOLEAN:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.booleanKey(
            applicationKey.getKey());
      case LONG:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.longKey(
            applicationKey.getKey());
      case DOUBLE:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.doubleKey(
            applicationKey.getKey());
      case STRING_ARRAY:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.stringArrayKey(
            applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.booleanArrayKey(
            applicationKey.getKey());
      case LONG_ARRAY:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.longArrayKey(
            applicationKey.getKey());
      case DOUBLE_ARRAY:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.doubleArrayKey(
            applicationKey.getKey());
      case EXTENDED_ATTRIBUTES:
        return io.opentelemetry.api.incubator.common.ExtendedAttributeKey.extendedAttributesKey(
            applicationKey.getKey());
    }
    logger.log(FINE, "unexpected attribute key type: {0}", applicationKey.getType());
    return null;
  }
}
