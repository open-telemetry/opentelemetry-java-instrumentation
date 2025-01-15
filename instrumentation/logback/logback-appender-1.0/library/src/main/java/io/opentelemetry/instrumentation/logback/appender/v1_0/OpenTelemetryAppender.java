/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private boolean captureExperimentalAttributes = false;
  private boolean captureCodeAttributes = false;
  private boolean captureMarkerAttribute = false;
  private boolean captureKeyValuePairAttributes = false;
  private boolean captureLoggerContext = false;
  private boolean captureArguments = false;
  private boolean captureLogstashAttributes = false;
  private List<String> captureMdcAttributes = emptyList();

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
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return;
    }
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
      logger.iteratorForAppenders().forEachRemaining(appender -> install(openTelemetry, appender));
    }
  }

  private static void install(OpenTelemetry openTelemetry, Appender<?> appender) {
    if (appender instanceof OpenTelemetryAppender) {
      ((OpenTelemetryAppender) appender).setOpenTelemetry(openTelemetry);
    } else if (appender instanceof AppenderAttachable) {
      ((AppenderAttachable<?>) appender)
          .iteratorForAppenders()
          .forEachRemaining(a -> OpenTelemetryAppender.install(openTelemetry, a));
    }
  }

  @Override
  public void start() {
    mapper =
        LoggingEventMapper.builder()
            .setCaptureExperimentalAttributes(captureExperimentalAttributes)
            .setCaptureMdcAttributes(captureMdcAttributes)
            .setCaptureCodeAttributes(captureCodeAttributes)
            .setCaptureMarkerAttribute(captureMarkerAttribute)
            .setCaptureKeyValuePairAttributes(captureKeyValuePairAttributes)
            .setCaptureLoggerContext(captureLoggerContext)
            .setCaptureArguments(captureArguments)
            .setCaptureLogstashAttributes(captureLogstashAttributes)
            .build();
    eventsToReplay = new ArrayBlockingQueue<>(numLogsCapturedBeforeOtelInstall);
    super.start();
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
   * @param captureKeyValuePairAttributes To enable or disable capturing key value pairs
   */
  public void setCaptureKeyValuePairAttributes(boolean captureKeyValuePairAttributes) {
    this.captureKeyValuePairAttributes = captureKeyValuePairAttributes;
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
   * Sets whether the arguments should be set to logs.
   *
   * @param captureArguments To enable or disable capturing logger arguments
   */
  public void setCaptureArguments(boolean captureArguments) {
    this.captureArguments = captureArguments;
  }

  /**
   * Sets whether the Logstash attributes should be set to logs.
   *
   * @param captureLogstashAttributes To enable or disable capturing Logstash attributes
   */
  public void setCaptureLogstashAttributes(boolean captureLogstashAttributes) {
    this.captureLogstashAttributes = captureLogstashAttributes;
  }

  /** Configures the {@link MDC} attributes that will be copied to logs. */
  public void setCaptureMdcAttributes(String attributes) {
    if (attributes != null) {
      captureMdcAttributes = filterBlanksAndNulls(attributes.split(","));
    } else {
      captureMdcAttributes = emptyList();
    }
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

  private void emit(OpenTelemetry openTelemetry, ILoggingEvent event) {
    mapper.emit(openTelemetry.getLogsBridge(), event, -1);
  }

  // copied from SDK's DefaultConfigProperties
  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
