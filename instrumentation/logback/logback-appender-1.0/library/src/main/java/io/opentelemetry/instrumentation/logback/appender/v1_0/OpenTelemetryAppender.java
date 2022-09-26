/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private static final AtomicReference<LoggerProvider> loggerProviderRef =
      new AtomicReference<>(LoggerProvider.noop());

  private volatile boolean captureExperimentalAttributes = false;
  private volatile boolean captureCodeAttributes = false;
  private volatile boolean captureMarkerAttribute = false;
  private volatile List<String> captureMdcAttributes = emptyList();

  private volatile LoggingEventMapper mapper;

  public OpenTelemetryAppender() {}

  @Override
  public void start() {
    mapper =
        new LoggingEventMapper(
            captureExperimentalAttributes,
            captureMdcAttributes,
            captureCodeAttributes,
            captureMarkerAttribute);
    super.start();
  }

  @Override
  protected void append(ILoggingEvent event) {
    mapper.emit(loggerProviderRef.get(), event);
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

  /** Configures the {@link MDC} attributes that will be copied to logs. */
  public void setCaptureMdcAttributes(String attributes) {
    if (attributes != null) {
      captureMdcAttributes = filterBlanksAndNulls(attributes.split(","));
    } else {
      captureMdcAttributes = emptyList();
    }
  }

  /**
   * Unsets the global {@link LoggerProvider}. This is only meant to be used from tests which need
   * to reconfigure {@link LoggerProvider}.
   */
  public static void resetSdkLogEmitterProviderForTest() {
    loggerProviderRef.set(LoggerProvider.noop());
  }

  // copied from SDK's DefaultConfigProperties
  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
