/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.List;

public final class LogbackSingletons {

  private static final LoggingEventMapper mapper;

  static {
    InstrumentationConfig config = AgentInstrumentationConfig.get();

    boolean captureExperimentalAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental-log-attributes", false);
    boolean captureCodeAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-code-attributes", false);
    boolean captureMarkerAttribute =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-marker-attribute", false);
    boolean captureKeyValuePairAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes",
            false);
    boolean captureLoggerContext =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes",
            false);
    boolean captureArguments =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-arguments", false);
    boolean captureLogstashAttributes =
        config.getBoolean(
            "otel.instrumentation.logback-appender.experimental.capture-logstash-attributes",
            false);
    List<String> captureMdcAttributes =
        config.getList(
            "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes",
            emptyList());

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
  }

  public static LoggingEventMapper mapper() {
    return mapper;
  }

  private LogbackSingletons() {}
}
