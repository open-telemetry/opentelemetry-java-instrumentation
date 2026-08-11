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
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.MDC;

public class LoggingEventMapper {

  private static final Logger logger = Logger.getLogger(LoggingEventMapper.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();
  private static final Cache<String, AttributeKey<String>> mdcAttributeKeys = Cache.bounded(100);

  private static final AttributeKey<String> LOG_BODY_TEMPLATE =
      AttributeKey.stringKey("log.body.template");
  private static final AttributeKey<List<String>> LOG_BODY_PARAMETERS =
      AttributeKey.stringArrayKey("log.body.parameters");

  private static final String DEPRECATED_CAPTURE_MDC_ATTRIBUTES =
      "otel.instrumentation.jboss-logmanager.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.included";
  private static final String MDC_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.jboss-logmanager.experimental.mdc-attributes.excluded";

  public static final LoggingEventMapper INSTANCE = new LoggingEventMapper();

  private final boolean captureExperimentalAttributes;
  private final boolean captureTemplate;
  private final boolean captureArguments;
  @Nullable private final Predicate<String> mdcAttributes;

  private LoggingEventMapper() {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "jboss_logmanager");
    captureExperimentalAttributes =
        config.getBoolean("experimental_log_attributes/development", false);
    captureTemplate = config.getBoolean("capture_template/development", false);
    captureArguments = config.getBoolean("capture_arguments/development", false);
    mdcAttributes = getMdcAttributes(config, SemconvStability.v3Preview(GlobalOpenTelemetry.get()));
  }

  @Nullable
  static Predicate<String> getMdcAttributes(DeclarativeConfigProperties config, boolean v3Preview) {
    DeclarativeConfigProperties mdcAttributes = config.get("mdc_attributes/development");
    List<String> included = mdcAttributes.getScalarList("included", String.class);
    List<String> excluded = mdcAttributes.getScalarList("excluded", String.class);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();

    if (v3Preview) {
      return selector.isEmpty() ? null : selector::matches;
    }

    // Deprecated include-only alias retained through 2.x.
    List<String> deprecatedIncluded =
        config.getScalarList("capture_mdc_attributes/development", String.class);
    if (!selector.isEmpty()) {
      if (deprecatedIncluded != null) {
        logWarningOnce(
            "precedence",
            "The "
                + DEPRECATED_CAPTURE_MDC_ATTRIBUTES
                + " setting and the equivalent declarative configuration property are deprecated"
                + " and ignored because "
                + MDC_ATTRIBUTES_INCLUDED
                + " or "
                + MDC_ATTRIBUTES_EXCLUDED
                + " is configured. They will be removed in 3.0.");
      }
      return selector::matches;
    }

    if (deprecatedIncluded == null) {
      return null;
    }
    logWarningOnce(
        "deprecation",
        "The "
            + DEPRECATED_CAPTURE_MDC_ATTRIBUTES
            + " setting and the equivalent declarative configuration property are deprecated and"
            + " will be removed in 3.0. Use "
            + MDC_ATTRIBUTES_INCLUDED
            + " or equivalent declarative configuration instead.");
    if (deprecatedIncluded.isEmpty()) {
      return null;
    }
    if (deprecatedIncluded.size() == 1 && deprecatedIncluded.get(0).equals("*")) {
      return key -> true;
    }
    Set<String> exactKeys = new HashSet<>(deprecatedIncluded);
    return exactKeys::contains;
  }

  private static void logWarningOnce(String warning, String message) {
    if (warnedDeprecatedProperties.add(warning)) {
      logger.warning(message);
    }
  }

  public void capture(org.jboss.logmanager.Logger logger, ExtLogRecord record) {
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

    if (mdcAttributes == null) {
      return;
    }

    for (Map.Entry<String, String> entry : context.entrySet()) {
      String key = entry.getKey();
      if (!OTEL_EVENT_NAME.getKey().equals(key) && mdcAttributes.test(key)) {
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
