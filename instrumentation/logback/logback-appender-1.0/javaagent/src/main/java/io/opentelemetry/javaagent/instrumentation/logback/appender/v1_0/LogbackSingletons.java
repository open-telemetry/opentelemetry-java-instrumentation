/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import java.util.function.Predicate;

public class LogbackSingletons {

  private static final LoggingEventMapper mapper;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "logback_appender");

    boolean captureExperimentalAttributes =
        config.getBoolean("experimental_log_attributes/development", false);
    boolean captureCodeAttributes = config.getBoolean("capture_code_attributes/development", false);
    boolean captureMarkerAttribute =
        config.getBoolean("capture_marker_attribute/development", false);
    boolean captureTemplate = config.getBoolean("capture_template/development", false);
    boolean captureArguments = config.getBoolean("capture_arguments/development", false);
    boolean captureLogstashMarkerAttributes =
        config.getBoolean("capture_logstash_marker_attributes/development", false);
    boolean captureLogstashStructuredArguments =
        config.getBoolean("capture_logstash_structured_arguments/development", false);
    Predicate<String> mdcAttributes =
        SelectorConfig.resolveLegacyLiteral(config, "logback-appender", "mdc-attributes");
    Predicate<String> keyValuePairAttributes =
        SelectorConfig.resolveLegacyBoolean(
            config, "logback-appender", "key-value-pair-attributes");
    Predicate<String> loggerContextAttributes =
        SelectorConfig.resolveLegacyBoolean(
            config, "logback-appender", "logger-context-attributes");

    mapper =
        LoggingEventMapper.builder()
            .setCaptureExperimentalAttributes(captureExperimentalAttributes)
            .setMdcAttributes(mdcAttributes)
            .setCaptureCodeAttributes(captureCodeAttributes)
            .setCaptureMarkerAttribute(captureMarkerAttribute)
            .setKeyValuePairAttributes(keyValuePairAttributes)
            .setLoggerContextAttributes(loggerContextAttributes)
            .setCaptureTemplate(captureTemplate)
            .setCaptureArguments(captureArguments)
            .setCaptureLogstashMarkerAttributes(captureLogstashMarkerAttributes)
            .setCaptureLogstashStructuredArguments(captureLogstashStructuredArguments)
            .build();
  }

  public static LoggingEventMapper mapper() {
    return mapper;
  }

  private LogbackSingletons() {}
}
