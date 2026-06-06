/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs;

import static application.io.opentelemetry.api.incubator.common.ExtendedAttributeType.EXTENDED_ATTRIBUTES;
import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.common.AttributeKey.valueKey;
import static java.util.logging.Level.FINE;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.KeyValue;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs.ApplicationLogRecordBuilder150;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAllAttributes(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributes attributes) {
    agentLogRecordBuilder.setAllAttributes(convertExtendedAttributes(attributes));
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
  @SuppressWarnings("unchecked") // converting ExtendedAttributeKey loses generic type
  public <T> application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setAttribute(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey<T> key, T value) {
    AttributeKey<T> agentKey = convertExtendedAttributeKey(key);
    if (agentKey != null) {
      if (key.getType() == EXTENDED_ATTRIBUTES) {
        // this cast is safe because T is Value<?> in this case
        value =
            (T)
                convertExtendedAttributesValue(
                    (application.io.opentelemetry.api.incubator.common.ExtendedAttributes) value);
      }
      agentLogRecordBuilder.setAttribute(agentKey, value);
    }
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder setException(
      Throwable throwable) {
    agentLogRecordBuilder.setException(throwable);
    return this;
  }

  @SuppressWarnings({
    "unchecked",
    "rawtypes", // converting ExtendedAttributeKey loses generic type
    "deprecation" // need to support applications still using EXTENDED_ATTRIBUTES
  })
  private static Attributes convertExtendedAttributes(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributes applicationAttributes) {
    AttributesBuilder agentAttributes = Attributes.builder();
    applicationAttributes.forEach(
        (key, value) -> {
          AttributeKey agentKey = convertExtendedAttributeKey(key);
          if (agentKey != null) {
            if (key.getType() == EXTENDED_ATTRIBUTES) {
              agentAttributes.put(
                  agentKey,
                  convertExtendedAttributesValue(
                      (application.io.opentelemetry.api.incubator.common.ExtendedAttributes) value));
            } else {
              agentAttributes.put(agentKey, value);
            }
          }
        });
    return agentAttributes.build();
  }

  @SuppressWarnings({
    "rawtypes", // converting ExtendedAttributeKey loses generic type
    "deprecation" // need to support applications still using EXTENDED_ATTRIBUTES
  })
  private static AttributeKey convertExtendedAttributeKey(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey applicationKey) {
    switch (applicationKey.getType()) {
      case STRING:
        return stringKey(applicationKey.getKey());
      case BOOLEAN:
        return booleanKey(applicationKey.getKey());
      case LONG:
        return longKey(applicationKey.getKey());
      case DOUBLE:
        return doubleKey(applicationKey.getKey());
      case STRING_ARRAY:
        return stringArrayKey(applicationKey.getKey());
      case BOOLEAN_ARRAY:
        return booleanArrayKey(applicationKey.getKey());
      case LONG_ARRAY:
        return longArrayKey(applicationKey.getKey());
      case DOUBLE_ARRAY:
        return doubleArrayKey(applicationKey.getKey());
      case EXTENDED_ATTRIBUTES:
        return valueKey(applicationKey.getKey());
    }
    logger.log(FINE, "unexpected attribute key type: {0}", applicationKey.getType());
    return null;
  }

  @SuppressWarnings("deprecation") // need to support applications still using EXTENDED_ATTRIBUTES
  private static Value<?> convertExtendedAttributesValue(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributes applicationAttributes) {
    List<KeyValue> values = new ArrayList<>();
    applicationAttributes.forEach(
        (key, value) -> {
          Value<?> agentValue = convertExtendedAttributeValue(key, value);
          if (agentValue != null) {
            values.add(KeyValue.of(key.getKey(), agentValue));
          }
        });
    return Value.of(values.toArray(new KeyValue[0]));
  }

  @Nullable
  @SuppressWarnings({"deprecation", "unchecked"}) // need to support deprecated EXTENDED_ATTRIBUTES
  private static Value<?> convertExtendedAttributeValue(
      application.io.opentelemetry.api.incubator.common.ExtendedAttributeKey<?> key, Object value) {
    switch (key.getType()) {
      case STRING:
        return Value.of((String) value);
      case BOOLEAN:
        return Value.of((Boolean) value);
      case LONG:
        return Value.of((Long) value);
      case DOUBLE:
        return Value.of((Double) value);
      case STRING_ARRAY:
        return convertArrayValue((List<String>) value);
      case BOOLEAN_ARRAY:
        return convertArrayValue((List<Boolean>) value);
      case LONG_ARRAY:
        return convertArrayValue((List<Long>) value);
      case DOUBLE_ARRAY:
        return convertArrayValue((List<Double>) value);
      case EXTENDED_ATTRIBUTES:
        return convertExtendedAttributesValue(
            (application.io.opentelemetry.api.incubator.common.ExtendedAttributes) value);
    }
    logger.log(FINE, "unexpected attribute key type: {0}", key.getType());
    return null;
  }

  private static Value<?> convertArrayValue(List<?> values) {
    List<Value<?>> result = new ArrayList<>();
    for (Object value : values) {
      if (value instanceof String) {
        result.add(Value.of((String) value));
      } else if (value instanceof Boolean) {
        result.add(Value.of((Boolean) value));
      } else if (value instanceof Long) {
        result.add(Value.of((Long) value));
      } else if (value instanceof Double) {
        result.add(Value.of((Double) value));
      }
    }
    return Value.of(result);
  }
}
