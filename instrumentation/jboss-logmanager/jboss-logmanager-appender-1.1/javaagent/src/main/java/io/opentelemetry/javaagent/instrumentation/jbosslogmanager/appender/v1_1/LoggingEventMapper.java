/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jbosslogmanager.appender.v1_1;

import static io.opentelemetry.semconv.OtelAttributes.OTEL_EVENT_NAME;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.sdk.common.internal.IncludeExcludePredicate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.MDC;

public class LoggingEventMapper {

  public static final LoggingEventMapper INSTANCE = new LoggingEventMapper();

  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  private static final AttributeKey<String> LOG_BODY_TEMPLATE =
      AttributeKey.stringKey("log.body.template");
  private static final AttributeKey<List<String>> LOG_BODY_PARAMETERS =
      AttributeKey.stringArrayKey("log.body.parameters");

  private static final boolean captureExperimentalAttributes;
  private static final boolean captureTemplate;
  private static final boolean captureArguments;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "jboss_logmanager");
    captureExperimentalAttributes =
        config.getBoolean("experimental_log_attributes/development", false);
    captureTemplate = config.getBoolean("capture_template/development", false);
    captureArguments = config.getBoolean("capture_arguments/development", false);
  }

  @Nullable private final Predicate<String> mdcAttributeFilter;

  private LoggingEventMapper() {
    List<String> captureMdcAttributes =
        DeclarativeConfigUtil.getInstrumentationConfig(
                GlobalOpenTelemetry.get(), "jboss_logmanager")
            .getScalarList("capture_mdc_attributes/development", String.class, emptyList());
    List<String> excludeMdcAttributes =
        DeclarativeConfigUtil.getInstrumentationConfig(
                GlobalOpenTelemetry.get(), "jboss_logmanager")
            .getScalarList("exclude_mdc_attributes/development", String.class, emptyList());
    // an empty include list captures nothing; excludes only take effect alongside includes
    this.mdcAttributeFilter =
        captureMdcAttributes.isEmpty()
            ? null
            : IncludeExcludePredicate.createPatternMatching(
                captureMdcAttributes, excludeMdcAttributes);
  }

  public void capture(Logger logger, ExtLogRecord record) {
    String instrumentationName = logger.getName();
    if (instrumentationName == null || instrumentationName.isEmpty()) {
      instrumentationName = "ROOT";
    }

    LogRecordBuilder builder =
        GlobalOpenTelemetry.get()
            .getLogsBridge()
            .loggerBuilder(instrumentationName)
            .build()
            .logRecordBuilder();

    String message = record.getFormattedMessage();
    if (message != null) {
      builder.setBody(message);
    }

    Object[] parameters = record.getParameters();
    if (parameters != null && parameters.length > 0) {
      if (captureTemplate) {
        builder.setAttribute(LOG_BODY_TEMPLATE, record.getMessage());
      }
      if (captureArguments) {
        builder.setAttribute(
            LOG_BODY_PARAMETERS, Arrays.stream(parameters).map(String::valueOf).collect(toList()));
      }
    }

    java.util.logging.Level level = record.getLevel();
    if (level != null) {
      builder.setSeverity(levelToSeverity(level));
      builder.setSeverityText(level.toString());
    }

    Throwable throwable = record.getThrown();
    if (throwable != null) {
      builder.setException(throwable);
    }
    captureMdcAttributes(builder);

    if (captureExperimentalAttributes) {
      Thread currentThread = Thread.currentThread();
      builder.setAttribute(THREAD_NAME, currentThread.getName());
      builder.setAttribute(THREAD_ID, currentThread.getId());
    }

    builder.setContext(Context.current());

    builder.setTimestamp(record.getMillis(), MILLISECONDS);
    builder.emit();
  }

  private void captureMdcAttributes(LogRecordBuilder builder) {

    Map<String, String> context = MDC.copy();
    if (context == null) {
      return;
    }

    String otelEventName = context.get(OTEL_EVENT_NAME.getKey());
    if (otelEventName != null) {
      builder.setEventName(otelEventName);
    }

    if (mdcAttributeFilter == null) {
      return;
    }

    for (Map.Entry<String, String> entry : context.entrySet()) {
      String key = entry.getKey();
      if (!OTEL_EVENT_NAME.getKey().equals(key) && mdcAttributeFilter.test(key)) {
        builder.setAttribute(getMdcAttributeKey(key), entry.getValue());
      }
    }
  }

  private static AttributeKey<String> getMdcAttributeKey(String key) {
    return mdcAttributeKeys.computeIfAbsent(key, AttributeKey::stringKey);
  }

  private static Severity levelToSeverity(java.util.logging.Level level) {
    int levelInt = level.intValue();
    if (levelInt >= Level.FATAL.intValue()) {
      return Severity.FATAL;
    } else if (levelInt >= Level.ERROR.intValue()) {
      return Severity.ERROR;
    } else if (levelInt >= Level.WARNING.intValue()) {
      return Severity.WARN;
    } else if (levelInt >= Level.INFO.intValue()) {
      return Severity.INFO;
    } else if (levelInt >= Level.DEBUG.intValue()) {
      return Severity.DEBUG;
    } else if (levelInt >= Level.TRACE.intValue()) {
      return Severity.TRACE;
    }
    return Severity.UNDEFINED_SEVERITY_NUMBER;
  }
}
