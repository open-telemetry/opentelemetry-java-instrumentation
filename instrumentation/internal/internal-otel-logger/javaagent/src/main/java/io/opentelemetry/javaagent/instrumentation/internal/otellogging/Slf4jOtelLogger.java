/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.otellogging;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Loopback;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.OtelLogger;
import io.opentelemetry.javaagent.bootstrap.logging.OtelLoggerBridge;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public final class Slf4jOtelLogger implements OtelLogger {

  private Slf4jOtelLogger() {}

  public static void install() {
    OtelLoggerBridge.installSlf4jLogger(new Slf4jOtelLogger());
  }

  @Override
  @SuppressWarnings("CheckReturnValue")
  public void record(Context context, String scopeName, @Nullable String eventName, @Nullable Value<?> bodyValue, Attributes attributes,
      Severity severity) {
    if (Loopback.isLoopbackOtelAppender(context.get(Loopback.loopbackContextKey))) {
      return;
    }

    // Ignore scope version, schemaUrl, attributes.
    // Ignore a variety of fields which exist for briding purposes. Here we're trying to bridge logs
    // recorded directly in the OpenTelemetry log API to SLF4J:
    // - logRecord.getSeverityText()
    // - logRecord.getTimestampEpochNanos()
    // - logRecord.getObservedTimestampEpochNanos()

    Logger logger = LoggerFactory.getLogger(scopeName);

    Level level = toSlf4jLevel(severity);

    if (!logger.isEnabledForLevel(level)) {
      return;
    }

    LoggingEventBuilder builder = logger.atLevel(level);

    if (bodyValue != null) {
      builder.setMessage(bodyValue.asString());
    }

    attributes
        .forEach(
            (key, value) -> {
              builder.addKeyValue(key.getKey(), value);
            });

    // append event_name last to take priority over attributes
    if (eventName != null) {
      builder.addKeyValue("event_name", eventName);
    }

    // Set loopback context and log
    MDC.put(Loopback.loopbackAttribute.getKey(), Boolean.toString(true));
    try (Scope scope =
        context.with(Loopback.loopbackContextKey, Loopback.withLoopbackOtelSdk(0)).makeCurrent()) {
      builder.log();
    }
  }

  private static Level toSlf4jLevel(Severity severity) {
    switch (severity) {
      case TRACE:
      case TRACE2:
      case TRACE3:
      case TRACE4:
        return Level.TRACE;
      case DEBUG:
      case DEBUG2:
      case DEBUG3:
      case DEBUG4:
        return Level.DEBUG;
      case INFO:
      case INFO2:
      case INFO3:
      case INFO4:
        return Level.INFO;
      case WARN:
      case WARN2:
      case WARN3:
      case WARN4:
        return Level.WARN;
      case ERROR:
      case ERROR2:
      case ERROR3:
      case ERROR4:
      case FATAL:
      case FATAL2:
      case FATAL3:
      case FATAL4:
        return Level.ERROR;
      case UNDEFINED_SEVERITY_NUMBER:
        return Level.INFO;
    }
    throw new IllegalArgumentException("Unknown severity: " + severity);
  }
}
