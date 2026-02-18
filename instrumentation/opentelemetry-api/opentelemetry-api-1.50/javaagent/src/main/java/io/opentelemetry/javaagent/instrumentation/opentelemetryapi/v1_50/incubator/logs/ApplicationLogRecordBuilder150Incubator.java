/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs;

import static application.io.opentelemetry.api.incubator.common.ExtendedAttributeType.EXTENDED_ATTRIBUTES;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.incubator.common.ExtendedAttributeKey;
import io.opentelemetry.api.incubator.common.ExtendedAttributes;
import io.opentelemetry.api.incubator.common.ExtendedAttributesBuilder;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLogRecordBuilder150;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class ApplicationLogRecordBuilder150Incubator extends ApplicationLogRecordBuilder150
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder {
  private static final Logger logger =
      Logger.getLogger(ApplicationLogRecordBuilder150Incubator.class.getName());

  private final ExtendedLogRecordBuilder agentLogRecordBuilder;

  ApplicationLogRecordBuilder150Incubator(LogRecordBuilder agentLogRecordBuilder) {
    super(agentLogRecordBuilder);
    this.agentLogRecordBuilder = (ExtendedLogRecordBuilder) agentLogRecordBuilder;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setEventName(
      String eventName) {
    agentLogRecordBuilder.setEventName(eventName);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setTimestamp(
      long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setTimestamp(
      Instant instant) {
    agentLogRecordBuilder.setTimestamp(instant);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      setObservedTimestamp(long timestamp, TimeUnit unit) {
    agentLogRecordBuilder.setObservedTimestamp(timestamp, unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      setObservedTimestamp(Instant instant) {
    agentLogRecordBuilder.setObservedTimestamp(instant);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setContext(
      application.io.opentelemetry.context.Context applicationContext) {
    agentLogRecordBuilder.setContext(AgentContextStorage.getAgentContext(applicationContext));
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setSeverity(
      application.io.opentelemetry.api.logs.Severity severity) {
    agentLogRecordBuilder.setSeverity(LogBridging.toAgent(severity));
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setSeverityText(
      String severityText) {
    agentLogRecordBuilder.setSeverityText(severityText);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setBody(
      String body) {
    agentLogRecordBuilder.setBody(body);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setBody(
      application.io.opentelemetry.api.common.Value<?> body) {
    agentLogRecordBuilder.setBody(convertValue(body));
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAllAttributes(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentLogRecordBuilder.setAllAttributes(Bridging.toAgent(applicationAttributes));
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAllAttributes(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributes attributes) {
    agentLogRecordBuilder.setAllAttributes(convertExtendedAttributes(attributes));
    return this;
  }

  @Override
  public <T> application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAttribute(
      application.io.opentelemetry.api.common.AttributeKey<T> key, @Nullable T value) {
    return (application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder)
        super.setAttribute(key, value);
  }

  @Override
  @SuppressWarnings("unchecked") // converting ExtendedAttributeKey loses generic type
  public <T> application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAttribute(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey<T> key, T value) {
    ExtendedAttributeKey<T> agentKey = convertExtendedAttributeKey(key);
    if (agentKey != null) {
      if (key.getType() == EXTENDED_ATTRIBUTES) {
        // this cast is safe because T is ExtendedAttributes in this case
        value =
            (T)
                convertExtendedAttributes(
                    (application.io.opentelemetry.api.incubator.common.ExtendedAttributes) value);
      }
      agentLogRecordBuilder.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setException(
      Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // converting ExtendedAttributeKey loses generic type
  private static ExtendedAttributes convertExtendedAttributes(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributes applicationAttributes) {
    ExtendedAttributesBuilder agentAttributes = ExtendedAttributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          ExtendedAttributeKey agentKey = convertExtendedAttributeKey(key);
          if (agentKey != null) {
            if (key.getType() == EXTENDED_ATTRIBUTES) {
              value =
                  convertExtendedAttributes(
                      (application.io.opentelemetry.api.incubator.common.ExtendedAttributes) value);
            }
            agentAttributes.put(agentKey, value);
          }
        });
    return agentAttributes.build();
  }

  @SuppressWarnings({
    "rawtypes", // converting ExtendedAttributeKey loses generic type
    "deprecation" // need to support applications still using EXTENDED_ATTRIBUTES
  })
  private static ExtendedAttributeKey convertExtendedAttributeKey(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return ExtendedAttributeKey.stringKey(applicationKey.getKey());
      case BOOLEAN:
        return ExtendedAttributeKey.booleanKey(applicationKey.getKey());
      case LONG:
        return ExtendedAttributeKey.longKey(applicationKey.getKey());
      case DOUBLE:
        return ExtendedAttributeKey.doubleKey(applicationKey.getKey());
      case STRING_ARRAY:
        return ExtendedAttributeKey.stringArrayKey(applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return ExtendedAttributeKey.booleanArrayKey(applicationKey.getKey());
      case LONG_ARRAY:
        return ExtendedAttributeKey.longArrayKey(applicationKey.getKey());
      case DOUBLE_ARRAY:
        return ExtendedAttributeKey.doubleArrayKey(applicationKey.getKey());
      case EXTENDED_ATTRIBUTES:
        return ExtendedAttributeKey.extendedAttributesKey(applicationKey.getKey());
    }
    logger.log(FINE, "unexpected attribute key type: {0}", applicationKey.getType());
    return null;
  }
}
