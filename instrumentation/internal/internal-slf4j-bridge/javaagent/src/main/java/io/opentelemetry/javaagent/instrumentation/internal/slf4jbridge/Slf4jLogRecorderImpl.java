/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.slf4jbridge;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.Slf4jLogRecorder;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.logging.Slf4jBridgeInstaller;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public final class Slf4jLogRecorderImpl implements Slf4jLogRecorder {

  private Slf4jLogRecorderImpl() {}

  public static void install() {
    Slf4jBridgeInstaller.installSlf4jLogger(new Slf4jLogRecorderImpl());
  }

  @Override
  public void record(
      Context context,
      String scopeName,
      @Nullable String eventName,
      @Nullable Value<?> bodyValue,
      Attributes attributes,
      Severity severity) {
    CallDepth callDepth = CallDepth.forClass(LoggerProvider.class);
    try {
      if (callDepth.getAndIncrement() > 0) {
        return;
      }
      recordToSlf4j(scopeName, eventName, bodyValue, attributes, severity);
    } finally {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("CheckReturnValue")
  public static void recordToSlf4j(
      String scopeName,
      @Nullable String eventName,
      @Nullable Value<?> bodyValue,
      Attributes attributes,
      Severity severity) {
    Logger logger = LoggerFactory.getLogger(scopeName);
    Level level = toSlf4jLevel(severity);
    if (!logger.isEnabledForLevel(level)) {
      return;
    }
    LoggingEventBuilder builder = logger.atLevel(level);
    if (bodyValue != null) {
      builder.setMessage(bodyValue.asString());
    }
    attributes.forEach((key, value) -> builder.addKeyValue(key.getKey(), value));

    // append event_name last to take priority over attributes
    if (eventName != null) {
      builder.addKeyValue("event_name", eventName);
    }
    builder.log();
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
