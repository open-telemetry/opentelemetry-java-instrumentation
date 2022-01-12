/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.appender.internal.LogBuilder;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterProvider;
import io.opentelemetry.instrumentation.api.appender.internal.Severity;
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class LoggingEventMapper {

  public static final LoggingEventMapper INSTANCE = new LoggingEventMapper();

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  private final List<String> captureMdcAttributes;

  // cached as an optimization
  private final boolean captureAllMdcAttributes;

  private LoggingEventMapper() {
    this(
        Config.get()
            .getList(
                "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes",
                emptyList()));
  }

  // visible for testing
  LoggingEventMapper(List<String> captureMdcAttributes) {
    this.captureMdcAttributes = captureMdcAttributes;
    this.captureAllMdcAttributes =
        captureMdcAttributes.size() == 1 && captureMdcAttributes.get(0).equals("*");
  }

  public void emit(LogEmitterProvider logEmitterProvider, ILoggingEvent event) {
    LogBuilder builder =
        logEmitterProvider.logEmitterBuilder(event.getLoggerName()).build().logBuilder();
    mapLoggingEvent(builder, event);
    builder.emit();
  }

  /**
   * Map the {@link ILoggingEvent} data model onto the {@link LogBuilder}. Unmapped fields include:
   *
   * <ul>
   *   <li>Thread name - {@link ILoggingEvent#getThreadName()}
   *   <li>Marker - {@link ILoggingEvent#getMarker()}
   *   <li>Mapped diagnostic context - {@link ILoggingEvent#getMDCPropertyMap()}
   * </ul>
   */
  private void mapLoggingEvent(LogBuilder builder, ILoggingEvent loggingEvent) {
    // message
    String message = loggingEvent.getMessage();
    if (message != null) {
      builder.setBody(message);
    }

    // time
    long timestamp = loggingEvent.getTimeStamp();
    builder.setEpoch(timestamp, TimeUnit.MILLISECONDS);

    // level
    Level level = loggingEvent.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.levelStr);
    }

    AttributesBuilder attributes = Attributes.builder();

    // throwable
    Object throwableProxy = loggingEvent.getThrowableProxy();
    Throwable throwable = null;
    if (throwableProxy instanceof ThrowableProxy) {
      // there is only one other subclass of ch.qos.logback.classic.spi.IThrowableProxy
      // and it is only used for logging exceptions over the wire
      throwable = ((ThrowableProxy) throwableProxy).getThrowable();
    }
    if (throwable != null) {
      setThrowable(attributes, throwable);
    }

    captureMdcAttributes(attributes, loggingEvent.getMDCPropertyMap());

    builder.setAttributes(attributes.build());

    // span context
    builder.setContext(Context.current());
  }

  // visible for testing
  void captureMdcAttributes(AttributesBuilder attributes, Map<String, String> mdcProperties) {

    if (captureAllMdcAttributes) {
      for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
        attributes.put(getMdcAttributeKey(entry.getKey()), entry.getValue());
      }
      return;
    }

    for (String key : captureMdcAttributes) {
      String value = mdcProperties.get(key);
      if (value != null) {
        attributes.put(getMdcAttributeKey(key), value);
      }
    }
  }

  public static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(key, k -> AttributeKey.stringKey("logback.mdc." + k));
  }

  private static void setThrowable(AttributesBuilder attributes, Throwable throwable) {
    // TODO (trask) extract method for recording exception into
    // instrumentation-appender-api-internal
    attributes.put(SemanticAttributes.EXCEPTION_TYPE, throwable.getClass().getName());
    attributes.put(SemanticAttributes.EXCEPTION_MESSAGE, throwable.getMessage());
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    attributes.put(SemanticAttributes.EXCEPTION_STACKTRACE, writer.toString());
  }

  private static Severity levelToSeverity(Level level) {
    switch (level.levelInt) {
      case Level.ALL_INT:
      case Level.TRACE_INT:
        return Severity.TRACE;
      case Level.DEBUG_INT:
        return Severity.DEBUG;
      case Level.INFO_INT:
        return Severity.INFO;
      case Level.WARN_INT:
        return Severity.WARN;
      case Level.ERROR_INT:
        return Severity.ERROR;
      case Level.OFF_INT:
      default:
        return Severity.UNDEFINED_SEVERITY_NUMBER;
    }
  }
}
