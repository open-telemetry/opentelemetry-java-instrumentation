/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.appender.v1_0;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.LoggingConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.logback.appender.v1_0.internal.LoggingEventMapper;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public class LogbackSingletons {

  // set by the logback-mdc instrumentation on the events whose MDC it adds the trace context to
  private static final VirtualField<ILoggingEvent, Context> MDC_CONTEXT =
      VirtualField.find(ILoggingEvent.class, Context.class);

  private static final LoggingEventMapper mapper;
  private static final LoggingEventMapper mdcInstrumentedMapper;

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
    Predicate<String> mdcAttributes =
        SelectorConfig.resolveLegacyLiteral(config, "logback-appender", "mdc-attributes");
    Predicate<String> loggerContextAttributes =
        SelectorConfig.resolveLegacyBoolean(
            config, "logback-appender", "logger-context-attributes");
    Predicate<String> structuredAttributes =
        LoggingConfig.resolveStructuredAttributes(GlobalOpenTelemetry.get());

    LoggingEventMapper.Builder builder =
        LoggingEventMapper.builder()
            .setCaptureExperimentalAttributes(captureExperimentalAttributes)
            .setMdcAttributes(mdcAttributes)
            .setCaptureCodeAttributes(captureCodeAttributes)
            .setCaptureMarkerAttribute(captureMarkerAttribute)
            .setStructuredAttributes(structuredAttributes)
            .setLoggerContextAttributes(loggerContextAttributes)
            .setCaptureTemplate(captureTemplate)
            .setCaptureArguments(captureArguments);
    mapper = builder.build();
    mdcInstrumentedMapper =
        builder.setMdcAttributes(excludeTraceContextKeys(mdcAttributes)).build();
  }

  // the exported log record already carries the trace context in dedicated fields
  @Nullable
  private static Predicate<String> excludeTraceContextKeys(@Nullable Predicate<String> selector) {
    if (selector == null) {
      return null;
    }
    CommonConfig commonConfig = AgentCommonConfig.get();
    String traceIdKey = commonConfig.getTraceIdKey();
    String spanIdKey = commonConfig.getSpanIdKey();
    String traceFlagsKey = commonConfig.getTraceFlagsKey();
    return key ->
        !key.equals(traceIdKey)
            && !key.equals(spanIdKey)
            && !key.equals(traceFlagsKey)
            && selector.test(key);
  }

  public static LoggingEventMapper mapper(ILoggingEvent event) {
    return MDC_CONTEXT.get(event) != null ? mdcInstrumentedMapper : mapper;
  }

  private LogbackSingletons() {}
}
