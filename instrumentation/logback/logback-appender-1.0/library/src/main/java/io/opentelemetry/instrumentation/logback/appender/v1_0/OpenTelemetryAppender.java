/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.instrumentation.logback.appender.v1_0.internal.AttributeSelectors.split;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.AttributeSelectors;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private boolean captureExperimentalAttributes = false;
  private boolean captureCodeAttributes = false;
  private boolean captureMarkerAttribute = false;
  private boolean captureLoggerContext = false;
  private boolean captureTemplate = false;
  private boolean captureArguments = false;
  private boolean captureLogstashMarkerAttributes = false;
  private boolean captureLogstashStructuredArguments = false;
  @Nullable private IncludeExclude mdcAttributes;
  @Nullable private String mdcAttributesIncluded;
  @Nullable private String mdcAttributesExcluded;
  @Nullable private String captureMdcAttributes;
  private final AtomicBoolean deprecatedMdcAttributesWarningLogged = new AtomicBoolean();
  @Nullable private IncludeExclude keyValuePairAttributes;
  @Nullable private String keyValuePairAttributesIncluded;
  @Nullable private String keyValuePairAttributesExcluded;
  @Nullable private Boolean captureKeyValuePairAttributes;
  private final AtomicBoolean deprecatedKeyValuePairAttributesWarningLogged = new AtomicBoolean();

  private volatile OpenTelemetry openTelemetry;
  private LoggingEventMapper mapper;

  private int numLogsCapturedBeforeOtelInstall = 1000;
  private BlockingQueue<LoggingEventToReplay> eventsToReplay =
      new ArrayBlockingQueue<>(numLogsCapturedBeforeOtelInstall);
  private final AtomicBoolean replayLimitWarningLogged = new AtomicBoolean();

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  public OpenTelemetryAppender() {}

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
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return;
    }
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (Logger logger : loggerContext.getLoggerList()) {
      logger
          .iteratorForAppenders()
          .forEachRemaining(appender -> forEachAppender(consumer, appender));
    }
  }

  private static void forEachAppender(
      Consumer<OpenTelemetryAppender> consumer, Appender<?> appender) {
    if (appender instanceof OpenTelemetryAppender) {
      consumer.accept((OpenTelemetryAppender) appender);
    } else if (appender instanceof AppenderAttachable) {
      ((AppenderAttachable<?>) appender)
          .iteratorForAppenders()
          .forEachRemaining(a -> forEachAppender(consumer, a));
    }
  }

  @Override
  public void start() {
    mapper =
        LoggingEventMapper.builder()
            .setCaptureExperimentalAttributes(captureExperimentalAttributes)
            .setMdcAttributes(resolveMdcAttributes())
            .setCaptureCodeAttributes(captureCodeAttributes)
            .setCaptureMarkerAttribute(captureMarkerAttribute)
            .setKeyValuePairAttributes(resolveKeyValuePairAttributes())
            .setCaptureLoggerContext(captureLoggerContext)
            .setCaptureTemplate(captureTemplate)
            .setCaptureArguments(captureArguments)
            .setCaptureLogstashMarkerAttributes(captureLogstashMarkerAttributes)
            .setCaptureLogstashStructuredArguments(captureLogstashStructuredArguments)
            .build();
    eventsToReplay = new ArrayBlockingQueue<>(numLogsCapturedBeforeOtelInstall);
    super.start();
  }

  @Nullable
  private Predicate<String> resolveMdcAttributes() {
    Predicate<String> selector = AttributeSelectors.create(mdcAttributes);
    if (selector == null) {
      selector =
          AttributeSelectors.create(
              IncludeExclude.builder()
                  .setIncluded(split(mdcAttributesIncluded))
                  .setExcluded(split(mdcAttributesExcluded))
                  .build());
    }
    if (selector != null) {
      return selector;
    }
    Predicate<String> deprecatedSelector =
        AttributeSelectors.createDeprecated(split(captureMdcAttributes));
    if (deprecatedSelector != null
        && deprecatedMdcAttributesWarningLogged.compareAndSet(false, true)) {
      addWarn(
          "The captureMdcAttributes setting of the OpenTelemetry appender and the"
              + " otel.instrumentation.logback-appender.experimental.capture-mdc-attributes"
              + " property are deprecated and may be removed in the next minor release. Use"
              + " mdcAttributesIncluded, mdcAttributesExcluded, or"
              + " otel.instrumentation.logback-appender.experimental.mdc-attributes.included"
              + " instead.");
    }
    return deprecatedSelector;
  }

  @Nullable
  private Predicate<String> resolveKeyValuePairAttributes() {
    Predicate<String> selector = AttributeSelectors.create(keyValuePairAttributes);
    if (selector == null) {
      selector =
          AttributeSelectors.create(
              IncludeExclude.builder()
                  .setIncluded(split(keyValuePairAttributesIncluded))
                  .setExcluded(split(keyValuePairAttributesExcluded))
                  .build());
    }
    if (selector != null) {
      return selector;
    }
    if (captureKeyValuePairAttributes != null
        && deprecatedKeyValuePairAttributesWarningLogged.compareAndSet(false, true)) {
      addWarn(
          "The captureKeyValuePairAttributes setting of the OpenTelemetry appender and the"
              + " otel.instrumentation.logback-appender.experimental"
              + ".capture-key-value-pair-attributes property are deprecated and may be removed in"
              + " the next minor release. Use keyValuePairAttributesIncluded,"
              + " keyValuePairAttributesExcluded, or otel.instrumentation.logback-appender"
              + ".experimental.key-value-pair-attributes.included instead.");
    }
    return AttributeSelectors.createDeprecated(captureKeyValuePairAttributes);
  }

  @SuppressWarnings("SystemOut")
  @Override
  protected void append(ILoggingEvent event) {
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

      LoggingEventToReplay logEventToReplay =
          new LoggingEventToReplay(event, captureExperimentalAttributes, captureCodeAttributes);

      if (!eventsToReplay.offer(logEventToReplay) && !replayLimitWarningLogged.getAndSet(true)) {
        String message =
            "numLogsCapturedBeforeOtelInstall value of the OpenTelemetry appender is too small.";
        System.err.println(message);
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Sets whether experimental attributes should be set to logs. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  public void setCaptureExperimentalAttributes(boolean captureExperimentalAttributes) {
    this.captureExperimentalAttributes = captureExperimentalAttributes;
  }

  /**
   * Sets whether the code attributes (file name, class name, method name and line number) should be
   * set to logs. Enabling these attributes can potentially impact performance (see
   * https://logback.qos.ch/manual/layouts.html).
   *
   * @param captureCodeAttributes To enable or disable the code attributes (file name, class name,
   *     method name and line number)
   */
  public void setCaptureCodeAttributes(boolean captureCodeAttributes) {
    this.captureCodeAttributes = captureCodeAttributes;
  }

  /**
   * Sets whether the marker attribute should be set to logs.
   *
   * @param captureMarkerAttribute To enable or disable capturing the marker attribute
   */
  public void setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
    this.captureMarkerAttribute = captureMarkerAttribute;
  }

  /**
   * Sets whether the key value pair attributes should be set to logs.
   *
   * <p>This setter backs the {@code captureKeyValuePairAttributes} element in {@code logback.xml}.
   *
   * @param captureKeyValuePairAttributes To enable or disable capturing key value pairs
   * @deprecated Use {@link #setKeyValuePairAttributesIncluded(String)} and {@link
   *     #setKeyValuePairAttributesExcluded(String)}, or {@link
   *     #setKeyValuePairAttributes(IncludeExclude)}, which select key value pair keys by glob
   *     pattern. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public void setCaptureKeyValuePairAttributes(boolean captureKeyValuePairAttributes) {
    this.captureKeyValuePairAttributes = captureKeyValuePairAttributes;
  }

  /**
   * Configures the key value pair attributes that will be copied to logs.
   *
   * <p>Key value pair keys and selector patterns are matched case-sensitively. {@code ?} matches
   * any single character and {@code *} matches any number of characters, including none, so {@code
   * *} captures all key value pair attributes. Excluded patterns take precedence over included
   * patterns, so a selector with only excluded patterns captures every key value pair attribute
   * that it does not exclude.
   *
   * <p>A {@code null} or empty selector leaves this appender without a programmatic selector, in
   * which case the key value pair attributes are selected by {@link
   * #setKeyValuePairAttributesIncluded(String)} and {@link
   * #setKeyValuePairAttributesExcluded(String)}. When these are also absent or empty, the
   * deprecated {@link #setCaptureKeyValuePairAttributes(boolean)} setting controls whether all key
   * value pair attributes are captured. Only a non-empty selector configured with this method takes
   * precedence over the other settings.
   *
   * <p>Captured key value pair attributes may contain sensitive information. Configure included and
   * excluded patterns to limit the data exported as log attributes.
   */
  public void setKeyValuePairAttributes(@Nullable IncludeExclude keyValuePairAttributes) {
    this.keyValuePairAttributes = keyValuePairAttributes;
  }

  /**
   * Configures the comma-separated key value pair key patterns that will be copied to logs.
   *
   * <p>This setter backs the {@code keyValuePairAttributesIncluded} element in {@code logback.xml}.
   * It is ignored when a non-empty selector is configured with {@link
   * #setKeyValuePairAttributes(IncludeExclude)}, and it takes precedence over the deprecated {@link
   * #setCaptureKeyValuePairAttributes(boolean)}.
   *
   * <p>Key value pair keys and patterns are matched case-sensitively. {@code ?} matches any single
   * character and {@code *} matches any number of characters, including none, so {@code *} captures
   * all key value pair attributes. Excluded patterns take precedence over included patterns.
   */
  public void setKeyValuePairAttributesIncluded(@Nullable String keyValuePairAttributesIncluded) {
    this.keyValuePairAttributesIncluded = keyValuePairAttributesIncluded;
  }

  /**
   * Configures the comma-separated key value pair key patterns that will not be copied to logs.
   *
   * <p>This setter backs the {@code keyValuePairAttributesExcluded} element in {@code logback.xml}.
   * It is ignored when a non-empty selector is configured with {@link
   * #setKeyValuePairAttributes(IncludeExclude)}, and it takes precedence over the deprecated {@link
   * #setCaptureKeyValuePairAttributes(boolean)}.
   *
   * <p>Key value pair keys and patterns are matched case-sensitively. {@code ?} matches any single
   * character and {@code *} matches any number of characters, including none. Excluded patterns
   * take precedence over included patterns, so configuring only excluded patterns captures every
   * key value pair attribute that they do not exclude.
   */
  public void setKeyValuePairAttributesExcluded(@Nullable String keyValuePairAttributesExcluded) {
    this.keyValuePairAttributesExcluded = keyValuePairAttributesExcluded;
  }

  /**
   * Sets whether the logger context properties should be set to logs.
   *
   * @param captureLoggerContext To enable or disable capturing logger context properties
   */
  public void setCaptureLoggerContext(boolean captureLoggerContext) {
    this.captureLoggerContext = captureLoggerContext;
  }

  /**
   * Sets whether the message template should be captured in logs if arguments are provided.
   *
   * @param captureTemplate whether the message template should be captured in logs if arguments are
   *     provided
   */
  public void setCaptureTemplate(boolean captureTemplate) {
    this.captureTemplate = captureTemplate;
  }

  /**
   * Sets whether the arguments should be set to logs.
   *
   * @param captureArguments To enable or disable capturing logger arguments
   */
  public void setCaptureArguments(boolean captureArguments) {
    this.captureArguments = captureArguments;
  }

  /** Sets whether the Logstash marker attributes should be captured. */
  public void setCaptureLogstashMarkerAttributes(boolean captureLogstashMarkerAttributes) {
    this.captureLogstashMarkerAttributes = captureLogstashMarkerAttributes;
  }

  /** Sets whether the Logstash StructuredArguments should be captured. */
  public void setCaptureLogstashStructuredArguments(boolean captureLogstashStructuredArguments) {
    this.captureLogstashStructuredArguments = captureLogstashStructuredArguments;
  }

  /**
   * Configures the {@link MDC} attributes that will be copied to logs.
   *
   * <p>MDC keys and selector patterns are matched case-sensitively. {@code ?} matches any single
   * character and {@code *} matches any number of characters, including none, so {@code *} captures
   * all MDC attributes. Excluded patterns take precedence over included patterns, so a selector
   * with only excluded patterns captures every MDC attribute that it does not exclude.
   *
   * <p>A {@code null} or empty selector leaves this appender without a programmatic selector, in
   * which case the MDC attributes are selected by {@link #setMdcAttributesIncluded(String)} and
   * {@link #setMdcAttributesExcluded(String)}, or, when those are absent or empty too, by the
   * deprecated {@link #setCaptureMdcAttributes(String)}. No MDC attributes are captured when all of
   * these are absent or empty. Only a non-empty selector configured with this method takes
   * precedence over the other settings.
   *
   * <p>Captured MDC attributes may contain sensitive information. Configure included and excluded
   * patterns to limit the data exported as log attributes.
   */
  public void setMdcAttributes(@Nullable IncludeExclude mdcAttributes) {
    this.mdcAttributes = mdcAttributes == null || mdcAttributes.isEmpty() ? null : mdcAttributes;
  }

  /**
   * Configures the comma-separated {@link MDC} key patterns that will be copied to logs.
   *
   * <p>This setter backs the {@code mdcAttributesIncluded} element in {@code logback.xml}. It is
   * ignored when a non-empty selector is configured with {@link #setMdcAttributes(IncludeExclude)},
   * and it takes precedence over the deprecated {@link #setCaptureMdcAttributes(String)}.
   *
   * <p>MDC keys and patterns are matched case-sensitively. {@code ?} matches any single character
   * and {@code *} matches any number of characters, including none, so {@code *} captures all MDC
   * attributes. Excluded patterns take precedence over included patterns.
   */
  public void setMdcAttributesIncluded(@Nullable String mdcAttributesIncluded) {
    this.mdcAttributesIncluded = mdcAttributesIncluded;
  }

  /**
   * Configures the comma-separated {@link MDC} key patterns that will not be copied to logs.
   *
   * <p>This setter backs the {@code mdcAttributesExcluded} element in {@code logback.xml}. It is
   * ignored when a non-empty selector is configured with {@link #setMdcAttributes(IncludeExclude)},
   * and it takes precedence over the deprecated {@link #setCaptureMdcAttributes(String)}.
   *
   * <p>MDC keys and patterns are matched case-sensitively. {@code ?} matches any single character
   * and {@code *} matches any number of characters, including none. Excluded patterns take
   * precedence over included patterns, so configuring only excluded patterns captures every MDC
   * attribute that they do not exclude.
   */
  public void setMdcAttributesExcluded(@Nullable String mdcAttributesExcluded) {
    this.mdcAttributesExcluded = mdcAttributesExcluded;
  }

  /**
   * Configures the comma-separated {@link MDC} keys that will be copied to logs.
   *
   * <p>Keys are matched by exact, case-sensitive equality, except that the single value {@code *}
   * captures all MDC attributes. This setting is ignored when a non-empty selector is configured
   * with {@link #setMdcAttributes(IncludeExclude)}, {@link #setMdcAttributesIncluded(String)} or
   * {@link #setMdcAttributesExcluded(String)}.
   *
   * @deprecated Use {@link #setMdcAttributesIncluded(String)} and {@link
   *     #setMdcAttributesExcluded(String)}, or {@link #setMdcAttributes(IncludeExclude)}, which
   *     select MDC keys by glob pattern. May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public void setCaptureMdcAttributes(@Nullable String captureMdcAttributes) {
    this.captureMdcAttributes = captureMdcAttributes;
  }

  /**
   * Log telemetry is emitted after the initialization of the OpenTelemetry Logback appender with an
   * {@link OpenTelemetry} object. This setting allows you to modify the size of the cache used to
   * replay the first logs.
   */
  public void setNumLogsCapturedBeforeOtelInstall(int size) {
    this.numLogsCapturedBeforeOtelInstall = size;
  }

  /**
   * Configures the {@link OpenTelemetry} used to append logs. This MUST be called for the appender
   * to function. See {@link #install(OpenTelemetry)} for simple installation option.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    List<LoggingEventToReplay> eventsToReplay = new ArrayList<>();
    Lock writeLock = lock.writeLock();
    writeLock.lock();
    try {
      // minimize scope of write lock
      this.openTelemetry = openTelemetry;
      // tests set openTelemetry to null, ignore it
      if (openTelemetry != null) {
        this.eventsToReplay.drainTo(eventsToReplay);
      }
    } finally {
      writeLock.unlock();
    }
    // now emit
    for (LoggingEventToReplay eventToReplay : eventsToReplay) {
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

  private void emit(OpenTelemetry openTelemetry, ILoggingEvent event) {
    mapper.emit(openTelemetry.getLogsBridge(), event, -1);
  }
}
