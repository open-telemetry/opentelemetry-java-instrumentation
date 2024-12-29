/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_17;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.LogEventMapper;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;

public final class Log4jHelper {

  private static final LogEventMapper<Map<String, String>> mapper;
  private static final boolean captureExperimentalAttributes;
  private static final MethodHandle stackTraceMethodHandle = getStackTraceMethodHandle();

  static {
    InstrumentationConfig config = AgentInstrumentationConfig.get();

    captureExperimentalAttributes =
        config.getBoolean("otel.instrumentation.log4j-appender.experimental-log-attributes", false);
    boolean captureCodeAttributes =
        config.getBoolean(
            "otel.instrumentation.log4j-appender.experimental.capture-code-attributes", false);
    boolean captureMapMessageAttributes =
        config.getBoolean(
            "otel.instrumentation.log4j-appender.experimental.capture-map-message-attributes",
            false);
    boolean captureMarkerAttribute =
        config.getBoolean(
            "otel.instrumentation.log4j-appender.experimental.capture-marker-attribute", false);
    List<String> captureContextDataAttributes =
        config.getList(
            "otel.instrumentation.log4j-appender.experimental.capture-mdc-attributes", emptyList());

    mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            captureExperimentalAttributes,
            captureCodeAttributes,
            captureMapMessageAttributes,
            captureMarkerAttribute,
            captureContextDataAttributes);
  }

  public static void capture(
      Logger logger,
      String loggerClassName,
      StackTraceElement location,
      Level level,
      Marker marker,
      Message message,
      Throwable throwable) {
    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogRecordBuilder builder =
        GlobalOpenTelemetry.get()
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .build()
            .logRecordBuilder();
    Map<String, String> contextData = ThreadContext.getImmutableContext();

    String threadName = null;
    long threadId = -1;
    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      threadName = currentThread.getName();
      threadId = currentThread.getId();
    }
    mapper.mapLogEvent(
        builder,
        message,
        level,
        marker,
        throwable,
        contextData,
        threadName,
        threadId,
        () -> location != null ? location : getLocation(loggerClassName),
        Context.current());
    builder.setTimestamp(Instant.now());
    builder.emit();
  }

  private static StackTraceElement getLocation(String loggerClassName) {
    if (stackTraceMethodHandle == null) {
      return null;
    }

    try {
      return (StackTraceElement) stackTraceMethodHandle.invoke(loggerClassName);
    } catch (Throwable exception) {
      return null;
    }
  }

  private static MethodHandle getStackTraceMethodHandle() {
    Class<?> stackTraceClass = null;
    try {
      // since 2.9.0
      stackTraceClass = Class.forName("org.apache.logging.log4j.util.StackLocatorUtil");
    } catch (ClassNotFoundException exception) {
      // ignore
    }
    if (stackTraceClass == null) {
      try {
        // before 2.9.0
        stackTraceClass = Class.forName("org.apache.logging.log4j.core.impl.Log4jLogEvent");
      } catch (ClassNotFoundException exception) {
        // ignore
      }
    }
    if (stackTraceClass == null) {
      return null;
    }
    try {
      return MethodHandles.lookup()
          .findStatic(
              stackTraceClass,
              "calcLocation",
              MethodType.methodType(StackTraceElement.class, String.class));
    } catch (Exception exception) {
      return null;
    }
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<Map<String, String>> {
    INSTANCE;

    @Override
    @Nullable
    public String getValue(Map<String, String> contextData, String key) {
      return contextData.get(key);
    }

    @Override
    public void forEach(Map<String, String> contextData, BiConsumer<String, String> action) {
      contextData.forEach(action);
    }
  }

  private Log4jHelper() {}
}
