/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.logs.data.Severity;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

final class LogEventMapper {

  private static final Map<Level, Severity> LEVEL_SEVERITY_MAP;

  static {
    Map<Level, Severity> levelSeverityMap = new HashMap<>();
    levelSeverityMap.put(Level.ALL, Severity.TRACE);
    levelSeverityMap.put(Level.TRACE, Severity.TRACE2);
    levelSeverityMap.put(Level.DEBUG, Severity.DEBUG);
    levelSeverityMap.put(Level.INFO, Severity.INFO);
    levelSeverityMap.put(Level.WARN, Severity.WARN);
    levelSeverityMap.put(Level.ERROR, Severity.ERROR);
    levelSeverityMap.put(Level.FATAL, Severity.FATAL);
    LEVEL_SEVERITY_MAP = Collections.unmodifiableMap(levelSeverityMap);
  }

  static LogBuilder toLogBuilder(LogEmitter logEmitter, LogEvent logEvent) {
    LogBuilder builder = logEmitter.logBuilder();
    builder.setBody(logEvent.getMessage().getFormattedMessage());
    builder.setSeverity(
        LEVEL_SEVERITY_MAP.getOrDefault(logEvent.getLevel(), Severity.UNDEFINED_SEVERITY_NUMBER));
    builder.setSeverityText(logEvent.getLevel().name());
    builder.setEpoch(logEvent.getNanoTime(), TimeUnit.NANOSECONDS);

    AttributesBuilder attributes = Attributes.builder();
    attributes.put("logger.name", logEvent.getLoggerName());
    attributes.put("thread.name", logEvent.getThreadName());

    Map<String, String> contextMap = logEvent.getContextData().toMap();
    if (!contextMap.isEmpty()) {
      if (contextMap.containsKey(TRACE_ID)) {
        builder.setTraceId(contextMap.remove(TRACE_ID));
      }
      if (contextMap.containsKey(SPAN_ID)) {
        builder.setSpanId(contextMap.remove(SPAN_ID));
      }
      contextMap.forEach(attributes::put);
    }

    builder.setAttributes(attributes.build());
    return builder;
  }

  private LogEventMapper() {}
}
