/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private boolean captureExperimentalAttributes = false;
  private boolean captureCodeAttributes = false;
  private boolean captureMarkerAttribute = false;
  private boolean captureKeyValuePairAttributes = false;
  private List<String> captureMdcAttributes = emptyList();

  private volatile OpenTelemetry openTelemetry;
  private LoggingEventMapper mapper;

  private BlockingQueue<LoggingEventToReplay> eventsToReplay = new ArrayBlockingQueue<>(1000);
  private final AtomicBoolean logCacheWarningDisplayed = new AtomicBoolean();

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
      logger
          .iteratorForAppenders()
          .forEachRemaining(
              appender -> {
                if (appender instanceof OpenTelemetryAppender) {
                  ((OpenTelemetryAppender) appender).setOpenTelemetry(openTelemetry);
                }
              });
    }
  }

  @Override
  public void start() {
    mapper =
        new LoggingEventMapper(
            captureExperimentalAttributes,
            captureMdcAttributes,
            captureCodeAttributes,
            captureMarkerAttribute,
            captureKeyValuePairAttributes);
    if (openTelemetry == null) {
      openTelemetry = OpenTelemetry.noop();
    }
    super.start();
  }

  @SuppressWarnings("SystemOut")
  @Override
  protected void append(ILoggingEvent event) {
    if (openTelemetry == OpenTelemetry.noop()) {
      if (eventsToReplay.remainingCapacity() > 0) {
        LoggingEventToReplay logEventToReplay =
            new LoggingEventToReplay(event, captureExperimentalAttributes, captureCodeAttributes);
        eventsToReplay.offer(logEventToReplay);
      } else if (!logCacheWarningDisplayed.getAndSet(true)) {
        String message =
            "Log cache size of the OpenTelemetry appender is too small. firstLogsCacheSize value has to be increased;";
        System.err.println(message);
      }
      return;
    }
    mapper.emit(
        openTelemetry.getLogsBridge(),
        new LoggingEventToReplay(event, captureExperimentalAttributes, captureCodeAttributes),
        -1);
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
   * @param captureMarkerAttribute To enable or disable the marker attribute
   */
  public void setCaptureMarkerAttribute(boolean captureMarkerAttribute) {
    this.captureMarkerAttribute = captureMarkerAttribute;
  }

  /**
   * Sets whether the key value pair attributes should be set to logs.
   *
   * @param captureKeyValuePairAttributes To enable or disable the marker attribute
   */
  public void setCaptureKeyValuePairAttributes(boolean captureKeyValuePairAttributes) {
    this.captureKeyValuePairAttributes = captureKeyValuePairAttributes;
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
  public void setFirstLogsCacheSize(int size) {
    eventsToReplay = new ArrayBlockingQueue<>(size);
  }

  /**
   * Configures the {@link OpenTelemetry} used to append logs. This MUST be called for the appender
   * to function. See {@link #install(OpenTelemetry)} for simple installation option.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    LoggingEventToReplay eventToReplay;
    while ((eventToReplay = eventsToReplay.poll()) != null) {
      mapper.emit(openTelemetry.getLogsBridge(), eventToReplay, -1);
    }
  }

  // copied from SDK's DefaultConfigProperties
  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
