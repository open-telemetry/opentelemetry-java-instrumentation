/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.List;

public final class LogbackSingletons {

  private static final LoggingEventMapper mapper;

  static {
    boolean captureExperimentalAttributes =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental_log_attributes")
            .orElse(false);
    boolean captureCodeAttributes =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_code_attributes")
            .orElse(false);
    boolean captureMarkerAttribute =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_marker_attribute")
            .orElse(false);
    boolean captureKeyValuePairAttributes =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_key_value_pair_attributes")
            .orElse(false);
    boolean captureLoggerContext =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_logger_context_attributes")
            .orElse(false);
    boolean captureTemplate =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_template")
            .orElse(false);
    boolean captureArguments =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_arguments")
            .orElse(false);
    boolean captureLogstashMarkerAttributes =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_logstash_marker_attributes")
            .orElse(false);
    boolean captureLogstashStructuredArguments =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_logstash_structured_arguments")
            .orElse(false);
    List<String> captureMdcAttributes =
        DeclarativeConfigUtil.getList(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_mdc_attributes")
            .orElse(emptyList());
    boolean captureEventName =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(),
                "java",
                "logback-appender",
                "experimental",
                "capture_event_name")
            .orElse(false);

    mapper =
        LoggingEventMapper.builder()
            .setCaptureExperimentalAttributes(captureExperimentalAttributes)
            .setCaptureMdcAttributes(captureMdcAttributes)
            .setCaptureCodeAttributes(captureCodeAttributes)
            .setCaptureMarkerAttribute(captureMarkerAttribute)
            .setCaptureKeyValuePairAttributes(captureKeyValuePairAttributes)
            .setCaptureLoggerContext(captureLoggerContext)
            .setCaptureTemplate(captureTemplate)
            .setCaptureArguments(captureArguments)
            .setCaptureLogstashMarkerAttributes(captureLogstashMarkerAttributes)
            .setCaptureLogstashStructuredArguments(captureLogstashStructuredArguments)
            .setCaptureEventName(captureEventName)
            .build();
  }

  public static LoggingEventMapper mapper() {
    return mapper;
  }

  private LogbackSingletons() {}
}
