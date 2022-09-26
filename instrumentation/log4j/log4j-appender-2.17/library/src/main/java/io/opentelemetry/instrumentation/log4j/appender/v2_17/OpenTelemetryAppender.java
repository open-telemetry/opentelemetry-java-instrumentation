/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.LogEventMapper;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
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

  private static final AtomicReference<LoggerProvider> loggerProviderRef =
      new AtomicReference<>(LoggerProvider.noop());

  private final LogEventMapper<ReadOnlyStringMap> mapper;

  @PluginBuilderFactory
  public static <B extends Builder<B>> B builder() {
    return new Builder<B>().asBuilder();
  }

  static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<OpenTelemetryAppender> {

    @PluginBuilderAttribute private boolean captureExperimentalAttributes;
    @PluginBuilderAttribute private boolean captureMapMessageAttributes;
    @PluginBuilderAttribute private boolean captureMarkerAttribute;
    @PluginBuilderAttribute private String captureContextDataAttributes;

    /**
     * Sets whether experimental attributes should be set to logs. These attributes may be changed
     * or removed in the future, so only enable this if you know you do not require attributes
     * filled by this instrumentation to be stable across versions.
     */
    public B setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
      this.captureExperimentalAttributes = captureExperimentalAttributes;
      return asBuilder();
    }

    /** Sets whether log4j {@link MapMessage} attributes should be copied to logs. */
    public B setCaptureMapMessageAttributes(boolean captureMapMessageAttributes) {
      this.captureMapMessageAttributes = captureMapMessageAttributes;
      return asBuilder();
    }

    /**
     * Sets whether the marker attribute should be set to logs.
     *
     * @param captureMarkerAttribute To enable or disable the marker attribute
     */
    public B setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
      this.captureMarkerAttribute = captureMarkerAttribute;
      return asBuilder();
    }

    /** Configures the {@link ThreadContext} attributes that will be copied to logs. */
    public B setCaptureContextDataAttributes(String captureContextDataAttributes) {
      this.captureContextDataAttributes = captureContextDataAttributes;
      return asBuilder();
    }

    @Override
    public OpenTelemetryAppender build() {
      return new OpenTelemetryAppender(
          getName(),
          getLayout(),
          getFilter(),
          isIgnoreExceptions(),
          getPropertyArray(),
          captureExperimentalAttributes,
          captureMapMessageAttributes,
          captureMarkerAttribute,
          captureContextDataAttributes);
    }
  }

  private OpenTelemetryAppender(
      String name,
      Layout<? extends Serializable> layout,
      Filter filter,
      boolean ignoreExceptions,
      Property[] properties,
      boolean captureExperimentalAttributes,
      boolean captureMapMessageAttributes,
      boolean captureMarkerAttribute,
      String captureContextDataAttributes) {

    super(name, filter, layout, ignoreExceptions, properties);
    this.mapper =
        new LogEventMapper<>(
            ContextDataAccessorImpl.INSTANCE,
            captureExperimentalAttributes,
            captureMapMessageAttributes,
            captureMarkerAttribute,
            splitAndFilterBlanksAndNulls(captureContextDataAttributes));
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

  @Override
  public void append(LogEvent event) {
    String instrumentationName = event.getLoggerName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }
    LogRecordBuilder builder =
        loggerProviderRef.get().loggerBuilder(instrumentationName).build().logRecordBuilder();
    ReadOnlyStringMap contextData = event.getContextData();
    mapper.mapLogEvent(
        builder,
        event.getMessage(),
        event.getLevel(),
        event.getMarker(),
        event.getThrown(),
        contextData);

    Instant timestamp = event.getInstant();
    if (timestamp != null) {
      builder.setEpoch(
          TimeUnit.MILLISECONDS.toNanos(timestamp.getEpochMillisecond())
              + timestamp.getNanoOfMillisecond(),
          TimeUnit.NANOSECONDS);
    }
    builder.emit();
  }

  /**
   * This should be called once as early as possible in your application initialization logic, often
   * in a {@code static} block in your main class. It should only be called once - an attempt to
   * call it a second time will result in an error. If trying to set the {@link LoggerProvider}
   * multiple times in tests, use {@link OpenTelemetryAppender#resetSdkLogEmitterProviderForTest()}
   * between them.
   */
  public static void setSdkLogEmitterProvider(LoggerProvider loggerProvider) {
    loggerProviderRef.set(loggerProvider);
  }

  /**
   * Unsets the global {@link LoggerProvider}. This is only meant to be used from tests which need
   * to reconfigure {@link LoggerProvider}.
   */
  public static void resetSdkLogEmitterProviderForTest() {
    loggerProviderRef.set(LoggerProvider.noop());
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<ReadOnlyStringMap> {
    INSTANCE;

    @Override
    @Nullable
    public Object getValue(ReadOnlyStringMap contextData, String key) {
      return contextData.getValue(key);
    }

    @Override
    public void forEach(ReadOnlyStringMap contextData, BiConsumer<String, Object> action) {
      contextData.forEach(action::accept);
    }
  }
}
