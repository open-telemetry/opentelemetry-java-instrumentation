/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataKeys.OTEL_CONTEXT_DATA_KEY;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.ContextDataAccessor;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.internal.LogEventMapper;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
  @Nullable private volatile OpenTelemetry openTelemetry;

  private final BlockingQueue<LogEventToReplay> eventsToReplay;
  private final AtomicBoolean replayLimitWarningLogged = new AtomicBoolean();
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final boolean captureCodeAttributes;

  /**
   * Installs the {@code openTelemetry} instance on any {@link OpenTelemetryAppender}s identified in
   * the {@link LoggerContext}.
   */
  public static void install(OpenTelemetry openTelemetry) {
    forEachAppender(appender -> appender.setOpenTelemetry(openTelemetry));
  }

  static void resetForTest() {
    forEachAppender(OpenTelemetryAppender::resetAppenderForTest);
  }

  private static void forEachAppender(Consumer<OpenTelemetryAppender> consumer) {
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
                consumer.accept((OpenTelemetryAppender) appender);
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
    @Nullable @PluginBuilderAttribute private String structuredAttributesIncluded;
    @Nullable @PluginBuilderAttribute private String structuredAttributesExcluded;
    @Nullable private IncludeExclude structuredAttributes;
    @PluginBuilderAttribute private boolean captureMarkerAttribute;
    @PluginBuilderAttribute private boolean captureTemplate;
    @PluginBuilderAttribute private boolean captureArguments;
    @Nullable @PluginBuilderAttribute private String captureContextDataAttributes;
    @Nullable @PluginBuilderAttribute private String contextDataAttributesIncluded;
    @Nullable @PluginBuilderAttribute private String contextDataAttributesExcluded;
    @Nullable private IncludeExclude contextDataAttributes;
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
    public B setCaptureCodeAttributes(boolean captureCodeAttributes) {
      this.captureCodeAttributes = captureCodeAttributes;
      return asBuilder();
    }

    /**
     * Configures the structured attributes copied from log4j {@link MapMessage} entries.
     *
     * <p>{@code MapMessage} keys and selector patterns are matched case-sensitively. {@code ?}
     * matches any single character and {@code *} matches any number of characters, including none,
     * so {@code included("*")} captures every structured attribute. Excluded patterns take
     * precedence over included patterns. A selector with only excluded patterns captures every
     * structured attribute that it does not exclude. Excluding {@code *} captures none.
     *
     * <p>Only a non-empty selector set here takes precedence over the {@code
     * structuredAttributesIncluded} and {@code structuredAttributesExcluded} settings. A {@code
     * null} or empty selector carries no configuration, so it does not disable capture and the next
     * configured source is used instead. All structured attributes are captured when the selector
     * and the pattern settings are absent or empty. Context data attributes are configured
     * separately.
     *
     * <p>Captured {@code MapMessage} attributes may contain sensitive information. Configure
     * included and excluded patterns to limit the data exported as log attributes.
     */
    @CanIgnoreReturnValue
    public B setStructuredAttributes(@Nullable IncludeExclude structuredAttributes) {
      this.structuredAttributes =
          structuredAttributes == null || structuredAttributes.isEmpty()
              ? null
              : structuredAttributes;
      return asBuilder();
    }

    /**
     * Configures the comma-separated structured attribute key patterns that will be copied to logs.
     *
     * <p>This is the configuration-file form of {@link #setStructuredAttributes(IncludeExclude)}
     * and is ignored when a non-empty selector is set with that method. Patterns use the same
     * case-sensitive glob syntax, where {@code ?} matches any single character and {@code *}
     * matches any number of characters, including none. Excluded patterns take precedence over
     * included patterns. Absent or empty pattern settings capture all structured attributes.
     */
    @CanIgnoreReturnValue
    public B setStructuredAttributesIncluded(String structuredAttributesIncluded) {
      this.structuredAttributesIncluded = structuredAttributesIncluded;
      return asBuilder();
    }

    /**
     * Configures the comma-separated structured attribute key patterns that will not be copied to
     * logs.
     *
     * <p>This is the configuration-file form of {@link #setStructuredAttributes(IncludeExclude)}
     * and is ignored when a non-empty selector is set with that method. Patterns use the same
     * case-sensitive glob syntax, where {@code ?} matches any single character and {@code *}
     * matches any number of characters, including none. Excluded patterns take precedence over
     * included patterns. Excluding {@code *} captures none. Absent or empty pattern settings
     * capture all structured attributes.
     */
    @CanIgnoreReturnValue
    public B setStructuredAttributesExcluded(String structuredAttributesExcluded) {
      this.structuredAttributesExcluded = structuredAttributesExcluded;
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

    /**
     * Sets whether the message template should be captured in logs if arguments are provided.
     *
     * @param captureTemplate whether the message template should be captured in logs if arguments
     *     are provided
     */
    @CanIgnoreReturnValue
    public B setCaptureTemplate(boolean captureTemplate) {
      this.captureTemplate = captureTemplate;
      return asBuilder();
    }

    /**
     * Sets whether the arguments should be captured in logs.
     *
     * @param captureArguments whether the arguments should be captured in logs
     */
    @CanIgnoreReturnValue
    public B setCaptureArguments(boolean captureArguments) {
      this.captureArguments = captureArguments;
      return asBuilder();
    }

    /**
     * Configures the {@link ThreadContext} attributes that will be copied to logs.
     *
     * <p>Context data keys and selector patterns are matched case-sensitively. {@code ?} matches
     * any single character and {@code *} matches any number of characters, including none, so
     * {@code included("*")} captures every context data attribute. Excluded patterns take
     * precedence over included patterns. A selector with only excluded patterns captures every
     * context data attribute that it does not exclude.
     *
     * <p>Only a non-empty selector set here takes precedence over the {@code
     * contextDataAttributesIncluded} and {@code contextDataAttributesExcluded} settings, which in
     * turn take precedence over the deprecated {@link #setCaptureContextDataAttributes(String)}
     * setting. A {@code null} or empty selector carries no configuration, so it does not disable
     * capture and the next configured source is used instead. No context data attributes are
     * captured only when every one of these sources is absent or empty.
     *
     * <p>Captured context data attributes may contain sensitive information. Configure included and
     * excluded patterns to limit the data exported as log attributes.
     */
    @CanIgnoreReturnValue
    public B setContextDataAttributes(@Nullable IncludeExclude contextDataAttributes) {
      this.contextDataAttributes =
          contextDataAttributes == null || contextDataAttributes.isEmpty()
              ? null
              : contextDataAttributes;
      return asBuilder();
    }

    /**
     * Configures the comma-separated {@link ThreadContext} attribute key patterns that will be
     * copied to logs.
     *
     * <p>This is the configuration-file form of {@link #setContextDataAttributes(IncludeExclude)}
     * and is ignored when a non-empty selector is set with that method. Patterns use the same
     * case-sensitive glob syntax, where {@code ?} matches any single character and {@code *}
     * matches any number of characters, including none.
     */
    @CanIgnoreReturnValue
    public B setContextDataAttributesIncluded(String contextDataAttributesIncluded) {
      this.contextDataAttributesIncluded = contextDataAttributesIncluded;
      return asBuilder();
    }

    /**
     * Configures the comma-separated {@link ThreadContext} attribute key patterns that will not be
     * copied to logs.
     *
     * <p>This is the configuration-file form of {@link #setContextDataAttributes(IncludeExclude)}
     * and is ignored when a non-empty selector is set with that method. Patterns use the same
     * case-sensitive glob syntax, where {@code ?} matches any single character and {@code *}
     * matches any number of characters, including none. Excluded patterns take precedence over
     * included patterns.
     */
    @CanIgnoreReturnValue
    public B setContextDataAttributesExcluded(String contextDataAttributesExcluded) {
      this.contextDataAttributesExcluded = contextDataAttributesExcluded;
      return asBuilder();
    }

    /**
     * Configures the {@link ThreadContext} attributes that will be copied to logs.
     *
     * <p>This setting does not support glob patterns. A comma-separated list containing only {@code
     * *} captures every context data attribute; otherwise every entry, including one that contains
     * {@code *} or {@code ?}, is matched as a literal context data key.
     *
     * <p>It is ignored when a non-empty selector is set with {@link
     * #setContextDataAttributes(IncludeExclude)} or when {@code contextDataAttributesIncluded} or
     * {@code contextDataAttributesExcluded} is configured.
     *
     * @deprecated Use {@link #setContextDataAttributes(IncludeExclude)} instead. May be removed in
     *     the next minor release.
     */
    @Deprecated // may be removed in the next minor release
    @CanIgnoreReturnValue
    public B setCaptureContextDataAttributes(String captureContextDataAttributes) {
      this.captureContextDataAttributes = captureContextDataAttributes;
      return asBuilder();
    }

    /**
     * Log telemetry is emitted after the initialization of the OpenTelemetry Log4j appender with an
     * {@link OpenTelemetry} object. This setting allows you to modify the size of the cache used to
     * replay the logs that were emitted prior to setting the OpenTelemetry instance into the
     * OpenTelemetry Log4j appender.
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
          getEffectiveStructuredAttributes(),
          captureMarkerAttribute,
          captureTemplate,
          captureArguments,
          getEffectiveContextDataAttributes(),
          numLogsCapturedBeforeOtelInstall,
          openTelemetry);
    }

    private Predicate<String> getEffectiveStructuredAttributes() {
      if (structuredAttributes != null) {
        return structuredAttributes::matches;
      }
      IncludeExclude selector =
          IncludeExclude.builder()
              .setIncluded(splitAndFilterBlanksAndNulls(structuredAttributesIncluded))
              .setExcluded(splitAndFilterBlanksAndNulls(structuredAttributesExcluded))
              .build();
      if (!selector.isEmpty()) {
        return selector::matches;
      }
      return value -> true;
    }

    @Nullable
    private Predicate<String> getEffectiveContextDataAttributes() {
      if (contextDataAttributes != null) {
        return contextDataAttributes::matches;
      }
      IncludeExclude selector =
          IncludeExclude.builder()
              .setIncluded(splitAndFilterBlanksAndNulls(contextDataAttributesIncluded))
              .setExcluded(splitAndFilterBlanksAndNulls(contextDataAttributesExcluded))
              .build();
      if (!selector.isEmpty()) {
        return selector::matches;
      }
      return SelectorConfig.resolveLegacyLiteral(
          splitAndFilterBlanksAndNulls(captureContextDataAttributes));
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
      Predicate<String> structuredAttributes,
      boolean captureMarkerAttribute,
      boolean captureTemplate,
      boolean captureArguments,
      @Nullable Predicate<String> contextDataAttributes,
      int numLogsCapturedBeforeOtelInstall,
      @Nullable OpenTelemetry openTelemetry) {
    super(name, filter, layout, ignoreExceptions, properties);

    this.mapper =
        createMapper(
            captureExperimentalAttributes,
            captureCodeAttributes,
            structuredAttributes,
            captureMarkerAttribute,
            captureTemplate,
            captureArguments,
            contextDataAttributes);
    this.openTelemetry = openTelemetry;
    this.captureCodeAttributes = captureCodeAttributes;
    if (numLogsCapturedBeforeOtelInstall != 0) {
      this.eventsToReplay = new ArrayBlockingQueue<>(numLogsCapturedBeforeOtelInstall);
    } else {
      this.eventsToReplay = new ArrayBlockingQueue<>(1000);
    }
  }

  private static List<String> splitAndFilterBlanksAndNulls(@Nullable String value) {
    if (value == null) {
      return emptyList();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(toList());
  }

  private static LogEventMapper<ReadOnlyStringMap> createMapper(
      boolean captureExperimentalAttributes,
      boolean captureCodeAttributes,
      @Nullable Predicate<String> mapMessageAttributes,
      boolean captureMarkerAttribute,
      boolean captureTemplate,
      boolean captureArguments,
      @Nullable Predicate<String> contextDataAttributes) {
    return new LogEventMapper<>(
        ContextDataAccessorImpl.INSTANCE,
        captureExperimentalAttributes,
        captureCodeAttributes,
        mapMessageAttributes,
        captureMarkerAttribute,
        captureTemplate,
        captureArguments,
        contextDataAttributes);
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

  private void resetAppenderForTest() {
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      openTelemetry = null;
      eventsToReplay.clear();
      replayLimitWarningLogged.set(false);
    } finally {
      writeLock.unlock();
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
    Context context = getContext(contextData);

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
          MILLISECONDS.toNanos(timestamp.getEpochMillisecond()) + timestamp.getNanoOfMillisecond(),
          NANOSECONDS);
    }
    builder.emit();
  }

  private static Context getContext(ReadOnlyStringMap contextData) {
    Object context = contextData.getValue(OTEL_CONTEXT_DATA_KEY);
    if (context instanceof Context) {
      return (Context) context;
    }
    return Context.current();
  }

  private enum ContextDataAccessorImpl implements ContextDataAccessor<ReadOnlyStringMap> {
    INSTANCE;

    @Override
    @Nullable
    public String getValue(ReadOnlyStringMap contextData, String key) {
      Object value = contextData.getValue(key);
      if (value instanceof String) {
        return (String) value;
      }
      return null;
    }

    @Override
    public void forEach(ReadOnlyStringMap contextData, BiConsumer<String, String> action) {
      contextData.forEach(
          (key, value) -> {
            if (value instanceof String) {
              action.accept(key, (String) value);
            }
          });
    }
  }
}
