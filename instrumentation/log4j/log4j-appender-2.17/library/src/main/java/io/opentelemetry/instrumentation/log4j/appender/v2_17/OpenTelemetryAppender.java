/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.LogEventMapper;
import io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal.ContextDataKeys;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.time.Instant;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

@Plugin(
    name = OpenTelemetryAppender.PLUGIN_NAME,
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE)
public class OpenTelemetryAppender extends AbstractAppender {

  static final String PLUGIN_NAME = "OpenTelemetry";

  private final LogEventMapper<ReadOnlyStringMap> mapper;
  private volatile OpenTelemetry openTelemetry;

  private final BlockingQueue<LogEventToReplay> eventsToReplay;
  private final AtomicBoolean replayLimitWarningLogged = new AtomicBoolean();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final boolean captureCodeAttributes;

  /**
   * Installs the {@code openTelemetry} instance on any {@link OpenTelemetryAppender}s identified in
   * the {@link LoggerContext}.
   */
  public static void install(OpenTelemetry openTelemetry) {
    org.apache.logging.log4j.spi.LoggerContext loggerContextSpi = LogManager.getContext(false);
    if (!(loggerContextSpi instanceof LoggerContext)) {
      return;
    }
    LoggerContext loggerContext = (LoggerContext) loggerContextSpi;
    Configuration config = loggerContext.getConfiguration();
    config
        .getAppenders()
        .values()
        .forEach(
            appender -> {
              if (appender instanceof OpenTelemetryAppender) {
                ((OpenTelemetryAppender) appender).setOpenTelemetry(openTelemetry);
              }
            });
  }

  @PluginBuilderFactory
  public static <B extends Builder<B>> B builder() {
    return new Builder<B>().asBuilder();
  }

  public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<OpenTelemetryAppender> {

    @PluginBuilderAttribute private boolean captureExperimentalAttributes;
    @PluginBuilderAttribute private boolean captureCodeAttributes;
    @PluginBuilderAttribute private boolean captureMapMessageAttributes;
    @PluginBuilderAttribute private boolean captureMarkerAttribute;
    @PluginBuilderAttribute private String captureContextDataAttributes;
    @PluginBuilderAttribute private int numLogsCapturedBeforeOtelInstall;

    @Nullable private OpenTelemetry openTelemetry;

    /**
     * Sets whether experimental attributes should be set to logs. These attributes may be changed
     * or removed in the future, so only enable this if you know you do not require attributes
     * filled by this instrumentation to be stable across versions.
     */
    @CanIgnoreReturnValue
    public B setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
      this.captureExperimentalAttributes = captureExperimentalAttributes;
      return asBuilder();
    }

    /**
     * Sets whether the code attributes (file name, class name, method name and line number) should
     * be set to logs. Enabling these attributes can potentially impact performance (see
     * https://logging.apache.org/log4j/2.x/manual/performance.html#layouts-location).
     *
     * @param captureCodeAttributes To enable or disable the code attributes (file name, class name,
     *     method name and line number)
     */
    @CanIgnoreReturnValue
    public B captureCodeAttributes(boolean captureCodeAttributes) {
      this.captureCodeAttributes = captureCodeAttributes;
      return asBuilder();
    }

    /** Sets whether log4j {@link MapMessage} attributes should be copied to logs. */
    @CanIgnoreReturnValue
    public B setCaptureMapMessageAttributes(boolean captureMapMessageAttributes) {
      this.captureMapMessageAttributes = captureMapMessageAttributes;
      return asBuilder();
    }

    /**
     * Sets whether the marker attribute should be set to logs.
     *
     * @param captureMarkerAttribute To enable or disable the marker attribute
     */
    @CanIgnoreReturnValue
    public B setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
      this.captureMarkerAttribute = captureMarkerAttribute;
      return asBuilder();
    }

    /** Configures the {@link ThreadContext} attributes that will be copied to logs. */
    @CanIgnoreReturnValue
    public B setCaptureContextDataAttributes(String captureContextDataAttributes) {
      this.captureContextDataAttributes = captureContextDataAttributes;
      return asBuilder();
    }

    /**
     * Log telemetry is emitted after the initialization of the OpenTelemetry Logback appender with
     * an {@link OpenTelemetry} object. This setting allows you to modify the size of the cache used
     * to replay the logs that were emitted prior to setting the OpenTelemetry instance into the
     * Logback appender.
     */
    @CanIgnoreReturnValue
    public B setNumLogsCapturedBeforeOtelInstall(int numLogsCapturedBeforeOtelInstall) {
      this.numLogsCapturedBeforeOtelInstall = numLogsCapturedBeforeOtelInstall;
      return asBuilder();
    }

    /** Configures the {@link OpenTelemetry} used to append logs. */
    @CanIgnoreReturnValue
    public B setOpenTelemetry(OpenTelemetry openTelemetry) {
      this.openTelemetry = openTelemetry;
      return asBuilder();
    }

    @Override
    public OpenTelemetryAppender build() {
      OpenTelemetry openTelemetry = this.openTelemetry;
      return new OpenTelemetryAppender(
          getName(),
          getLayout(),
          getFilter(),
          isIgnoreExceptions(),
          getPropertyArray(),
          captureExperimentalAttributes,
          captureCodeAttributes,
          captureMapMessageAttributes,
          captureMarkerAttribute,
          captureContextDataAttributes,
          numLogsCapturedBeforeOtelInstall,
          openTelemetry);
    }
  }

  private OpenTelemetryAppender(
      String name,
      Layout<? extends Serializable> layout,
      Filter filter,
      boolean ignoreExceptions,
      Property[] properties,
      boolean captureExperimentalAttributes,
      boolean captureCodeAttributes,
      boolean captureMapMessageAttributes,
      boolean captureMarkerAttribute,
      String captureContextDataAttributes,
      int numLogsCapturedBeforeOtelInstall,
      OpenTelemetry openTelemetry) {

    super(name, filter, layout, ignoreExceptions, properties);
    this.mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            captureExperimentalAttributes,
            captureCodeAttributes,
            captureMapMessageAttributes,
            captureMarkerAttribute,
            splitAndFilterBlanksAndNulls(captureContextDataAttributes));
    this.openTelemetry = openTelemetry;
    this.captureCodeAttributes = captureCodeAttributes;
    if (numLogsCapturedBeforeOtelInstall != 0) {
      this.eventsToReplay = new ArrayBlockingQueue<>(numLogsCapturedBeforeOtelInstall);
    } else {
      this.eventsToReplay = new ArrayBlockingQueue<>(1000);
    }
  }

  private static List<String> splitAndFilterBlanksAndNulls(String value) {
    if (value == null) {
      return emptyList();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  /**
   * Configures the {@link OpenTelemetry} used to append logs. This MUST be called for the appender
   * to function. See {@link #install(OpenTelemetry)} for simple installation option.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    List<LogEventToReplay> eventsToReplay = new ArrayList<>();
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      // minimize scope of write lock
      this.openTelemetry = openTelemetry;
      this.eventsToReplay.drainTo(eventsToReplay);
    } finally {
      writeLock.unlock();
    }
    // now emit
    for (LogEventToReplay eventToReplay : eventsToReplay) {
      emit(openTelemetry, eventToReplay);
    }
  }

  @SuppressWarnings("SystemOut")
  @Override
  public void append(LogEvent event) {
    OpenTelemetry openTelemetry = this.openTelemetry;
    if (openTelemetry != null) {
      // optimization to avoid locking after the OpenTelemetry instance is set
      emit(openTelemetry, event);
      return;
    }

    Lock readLock = lock.readLock();
    readLock.lock();
    try {
      openTelemetry = this.openTelemetry;
      if (openTelemetry != null) {
        emit(openTelemetry, event);
        return;
      }

      LogEventToReplay logEventToReplay = new LogEventToReplay(event, captureCodeAttributes);

      if (!eventsToReplay.offer(logEventToReplay) && !replayLimitWarningLogged.getAndSet(true)) {
        String message =
            "numLogsCapturedBeforeOtelInstall value of the OpenTelemetry appender is too small.";
        System.err.println(message);
      }
    } finally {
      readLock.unlock();
    }
  }

  private void emit(OpenTelemetry openTelemetry, LogEvent event) {
    String instrumentationName = event.getLoggerName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }

    LogRecordBuilder builder =
        openTelemetry.getLogsBridge().loggerBuilder(instrumentationName).build().logRecordBuilder();
    ReadOnlyStringMap contextData = event.getContextData();
    Context context = Context.current();
    // when using async logger we'll be executing on a different thread than what started logging
    // reconstruct the context from context data
    if (context == Context.root()) {
      ContextDataAccessor<ReadOnlyStringMap> contextDataAccessor = ContextDataAccessorImpl.INSTANCE;
      String traceId = contextDataAccessor.getValue(contextData, ContextDataKeys.TRACE_ID_KEY);
      String spanId = contextDataAccessor.getValue(contextData, ContextDataKeys.SPAN_ID_KEY);
      String traceFlags =
          contextDataAccessor.getValue(contextData, ContextDataKeys.TRACE_FLAGS_KEY);
      if (traceId != null && spanId != null && traceFlags != null) {
        context =
            Context.root()
                .with(
                    Span.wrap(
                        SpanContext.create(
                            traceId,
                            spanId,
                            TraceFlags.fromHex(traceFlags, 0),
                            TraceState.getDefault())));
      }
    }

    mapper.mapLogEvent(
        builder,
        event.getMessage(),
        event.getLevel(),
        event.getMarker(),
        event.getThrown(),
        contextData,
        event.getThreadName(),
        event.getThreadId(),
        event::getSource,
        context);

    Instant timestamp = event.getInstant();
    if (timestamp != null) {
      builder.setTimestamp(
          TimeUnit.MILLISECONDS.toNanos(timestamp.getEpochMillisecond())
              + timestamp.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }
    builder.emit();
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<ReadOnlyStringMap> {
    INSTANCE;

    @Override
    @Nullable
    public String getValue(ReadOnlyStringMap contextData, String key) {
      return contextData.getValue(key);
    }

    @Override
    public void forEach(ReadOnlyStringMap contextData, BiConsumer<String, String> action) {
      contextData.forEach(action::accept);
    }
  }
}
