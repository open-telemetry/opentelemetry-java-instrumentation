/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.MDC;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  private boolean captureExperimentalAttributes = false;
  private boolean captureCodeAttributes = false;
  private boolean captureMarkerAttribute = false;
  private boolean captureKeyValuePairAttributes = false;
  private List<String> captureMdcAttributes = emptyList();
  private boolean useGlobalOpenTelemetry = false;

  private OpenTelemetry openTelemetry;
  private LoggingEventMapper mapper;

  public OpenTelemetryAppender() {}

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
      openTelemetry =
          this.useGlobalOpenTelemetry ? GlobalOpenTelemetry.get() : OpenTelemetry.noop();
    }
    super.start();
  }

  @Override
  protected void append(ILoggingEvent event) {
    mapper.emit(openTelemetry.getLogsBridge(), event);
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
   * Sets the {@link OpenTelemetry} used to append logs. Takes precedent over {@link
   * #setUseGlobalOpenTelemetry(boolean)}.
   */
  public void setOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets whether to use {@link io.opentelemetry.api.GlobalOpenTelemetry}. Defaults to false.
   * Required for a pure XML configuration approach. If {@code false}, you MUST programmatically
   * call {@link #setOpenTelemetry(OpenTelemetry)}.
   */
  public void setUseGlobalOpenTelemetry(boolean useGlobalOpenTelemetry) {
    this.useGlobalOpenTelemetry = useGlobalOpenTelemetry;
  }

  // copied from SDK's DefaultConfigProperties
  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }
}
