/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static io.opentelemetry.instrumentation.logback.appender.v1_0.internal.AttributeSelectors.split;
import static java.util.Collections.emptyList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

class LogbackAppenderInstaller {
  private static final Logger logger = LoggerFactory.getLogger(LogbackAppenderInstaller.class);

  private static final String DEPRECATED_MDC_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.included";
  private static final String MDC_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded";
  private static final String DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes";
  private static final String LOGGER_CONTEXT_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.logger-context-attributes.included";
  private static final String LOGGER_CONTEXT_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.logback-appender.experimental.logger-context-attributes.excluded";
  private static final String DEPRECATED_KEY_VALUE_PAIR_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes";
  private static final String KEY_VALUE_PAIR_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.key-value-pair-attributes.included";
  private static final String KEY_VALUE_PAIR_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.logback-appender.experimental.key-value-pair-attributes.excluded";

  static void install(ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    Optional<io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender>
        existingMdcAppender =
            findAppender(
                io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender.class);
    if (existingMdcAppender.isPresent()) {
      initializeMdcAppenderFromProperties(
          applicationEnvironmentPreparedEvent, existingMdcAppender.get());
    } else if (isLogbackMdcAppenderAddable(applicationEnvironmentPreparedEvent)) {
      addMdcAppender(applicationEnvironmentPreparedEvent);
    }

    Optional<OpenTelemetryAppender> existingOpenTelemetryAppender =
        findAppender(OpenTelemetryAppender.class);
    if (existingOpenTelemetryAppender.isPresent()) {
      reInitializeOpenTelemetryAppender(
          existingOpenTelemetryAppender, applicationEnvironmentPreparedEvent);
    } else if (isLogbackAppenderAddable(applicationEnvironmentPreparedEvent)) {
      addOpenTelemetryAppender(applicationEnvironmentPreparedEvent);
    }
  }

  private static boolean isLogbackAppenderAddable(ApplicationEnvironmentPreparedEvent event) {
    return isAppenderAddable(event, "logback-appender");
  }

  private static boolean isLogbackMdcAppenderAddable(ApplicationEnvironmentPreparedEvent event) {
    return isAppenderAddable(event, "logback-mdc");
  }

  private static boolean isAppenderAddable(ApplicationEnvironmentPreparedEvent event, String name) {
    ConfigurableEnvironment environment = event.getEnvironment();
    return EarlyConfig.otelEnabled(environment)
        && EarlyConfig.isInstrumentationEnabled(environment, name, true);
  }

  private static void reInitializeOpenTelemetryAppender(
      Optional<OpenTelemetryAppender> existingOpenTelemetryAppender,
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    OpenTelemetryAppender openTelemetryAppender = existingOpenTelemetryAppender.get();
    // The OpenTelemetry appender is stopped and restarted from the
    // org.springframework.boot.context.logging.LoggingApplicationListener.initialize
    // method.
    // The OpenTelemetryAppender initializes the LoggingEventMapper in the start() method. So, here
    // we stop the OpenTelemetry appender before its re-initialization and its restart.
    openTelemetryAppender.stop();
    initializeOpenTelemetryAppenderFromProperties(
        applicationEnvironmentPreparedEvent, openTelemetryAppender);
    openTelemetryAppender.start();
  }

  private static void addOpenTelemetryAppender(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    ch.qos.logback.classic.Logger logbackLogger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);
    OpenTelemetryAppender openTelemetryAppender = new OpenTelemetryAppender();
    openTelemetryAppender.setContext(logbackLogger.getLoggerContext());
    initializeOpenTelemetryAppenderFromProperties(
        applicationEnvironmentPreparedEvent, openTelemetryAppender);
    openTelemetryAppender.start();
    logbackLogger.addAppender(openTelemetryAppender);
  }

  private static void initializeOpenTelemetryAppenderFromProperties(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      OpenTelemetryAppender openTelemetryAppender) {

    // Reading configuration directly from Spring Environment instead of using declarative
    // configuration because this code runs during ApplicationEnvironmentPreparedEvent, which occurs
    // before the full Spring application context is available. This is the same approach used by
    // org.springframework.boot.context.logging.LoggingApplicationListener.
    // The evaluateBooleanProperty method handles both declarative and non-declarative config by
    // transforming property names when declarative config is detected.
    Boolean codeAttribute =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-code-attributes");
    if (codeAttribute != null) {
      openTelemetryAppender.setCaptureCodeAttributes(codeAttribute);
    }

    Boolean markerAttribute =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-marker-attribute");
    if (markerAttribute != null) {
      openTelemetryAppender.setCaptureMarkerAttribute(markerAttribute);
    }

    Boolean logAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental-log-attributes");
    if (logAttributes != null) {
      openTelemetryAppender.setCaptureExperimentalAttributes(logAttributes);
    }

    Boolean captureTemplate =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-template");
    if (captureTemplate != null) {
      openTelemetryAppender.setCaptureTemplate(captureTemplate);
    }

    Boolean captureArguments =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-arguments");
    if (captureArguments != null) {
      openTelemetryAppender.setCaptureArguments(captureArguments);
    }

    Boolean captureLogstashMarkerAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-logstash-marker-attributes");
    if (captureLogstashMarkerAttributes != null) {
      openTelemetryAppender.setCaptureLogstashMarkerAttributes(captureLogstashMarkerAttributes);
    }

    Boolean captureLogstashStructuredArguments =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-logstash-structured-arguments");
    if (captureLogstashStructuredArguments != null) {
      openTelemetryAppender.setCaptureLogstashStructuredArguments(
          captureLogstashStructuredArguments);
    }

    initializeMdcAttributesFromProperties(
        applicationEnvironmentPreparedEvent.getEnvironment(), openTelemetryAppender);
    initializeKeyValuePairAttributesFromProperties(
        applicationEnvironmentPreparedEvent.getEnvironment(), openTelemetryAppender);
    initializeLoggerContextAttributesFromProperties(
        applicationEnvironmentPreparedEvent.getEnvironment(), openTelemetryAppender);
  }

  // the appender resolves the precedence between these settings, ignoring the deprecated one when
  // a non-empty selector is configured
  @SuppressWarnings("deprecation") // the deprecated setter preserves the deprecated semantics
  static void initializeMdcAttributesFromProperties(
      ConfigurableEnvironment environment, OpenTelemetryAppender openTelemetryAppender) {
    List<String> included = getLoggingListProperty(environment, MDC_ATTRIBUTES_INCLUDED);
    List<String> excluded = getLoggingListProperty(environment, MDC_ATTRIBUTES_EXCLUDED);
    List<String> deprecated = getLoggingListProperty(environment, DEPRECATED_MDC_ATTRIBUTES);
    // an empty selector property is equivalent to an unset one, matching how the same flat
    // properties are read outside of Spring, where empty values cannot be distinguished from unset
    // ones
    if (isEmpty(included) && isEmpty(excluded) && isEmpty(deprecated)) {
      return;
    }

    // configuration properties replace the MDC settings of an appender declared in logback.xml, so
    // every source the appender resolves is set, including the ones that are not configured
    openTelemetryAppender.setMdcAttributes(
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build());
    openTelemetryAppender.setMdcAttributesIncluded(null);
    openTelemetryAppender.setMdcAttributesExcluded(null);
    // the deprecated appender setting splits its value on commas, so joining the configured keys
    // reproduces them exactly, including the single "*" that selects every MDC key
    openTelemetryAppender.setCaptureMdcAttributes(
        deprecated == null ? null : String.join(",", deprecated));
  }

  // the appender resolves the precedence between these settings, ignoring the deprecated one when
  // a non-empty selector is configured
  @SuppressWarnings("deprecation") // the deprecated setter preserves the deprecated semantics
  static void initializeKeyValuePairAttributesFromProperties(
      ConfigurableEnvironment environment, OpenTelemetryAppender openTelemetryAppender) {
    List<String> included = getLoggingListProperty(environment, KEY_VALUE_PAIR_ATTRIBUTES_INCLUDED);
    List<String> excluded = getLoggingListProperty(environment, KEY_VALUE_PAIR_ATTRIBUTES_EXCLUDED);
    Boolean deprecated = evaluateBooleanProperty(environment, DEPRECATED_KEY_VALUE_PAIR_ATTRIBUTES);
    // an empty selector property is equivalent to an unset one, matching how the same flat
    // properties are read outside of Spring, where empty values cannot be distinguished from unset
    // ones
    if (isEmpty(included) && isEmpty(excluded) && deprecated == null) {
      return;
    }

    // configuration properties replace the key value pair settings of an appender declared in
    // logback.xml, so every source the appender resolves is set, including the ones that are not
    // configured
    openTelemetryAppender.setKeyValuePairAttributes(
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build());
    openTelemetryAppender.setKeyValuePairAttributesIncluded(null);
    openTelemetryAppender.setKeyValuePairAttributesExcluded(null);
    // reaching here with an empty selector implies that the deprecated property is configured, so
    // the settings declared in logback.xml never survive as a fallback
    if (deprecated != null) {
      openTelemetryAppender.setCaptureKeyValuePairAttributes(deprecated);
    }
  }


  // the appender resolves the precedence between these settings, ignoring the deprecated one when
  // a non-empty selector is configured
  @SuppressWarnings("deprecation") // the deprecated setter preserves the deprecated semantics
  static void initializeLoggerContextAttributesFromProperties(
      ConfigurableEnvironment environment, OpenTelemetryAppender openTelemetryAppender) {
    List<String> included = getLoggingListProperty(environment, LOGGER_CONTEXT_ATTRIBUTES_INCLUDED);
    List<String> excluded = getLoggingListProperty(environment, LOGGER_CONTEXT_ATTRIBUTES_EXCLUDED);
    Boolean deprecated = evaluateBooleanProperty(environment, DEPRECATED_LOGGER_CONTEXT_ATTRIBUTES);
    // an empty selector property is equivalent to an unset one, matching how the same flat
    // properties are read outside of Spring, where empty values cannot be distinguished from unset
    // ones
    if (isEmpty(included) && isEmpty(excluded) && deprecated == null) {
      return;
    }

    // configuration properties replace the logger context settings of an appender declared in
    // logback.xml, so every source the appender resolves is set, including the ones that are not
    // configured
    openTelemetryAppender.setLoggerContextAttributes(
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build());
    openTelemetryAppender.setLoggerContextAttributesIncluded(null);
    openTelemetryAppender.setLoggerContextAttributesExcluded(null);
    // reaching here with an empty selector implies that the deprecated property is configured, so
    // the settings declared in logback.xml never survive as a fallback
    if (deprecated != null) {
      openTelemetryAppender.setCaptureLoggerContext(deprecated);
    }
  }

  private static boolean isEmpty(@Nullable List<String> values) {
    return values == null || values.isEmpty();
  }

  /**
   * Reads a list-valued property, which declarative configuration flattens into indexed properties
   * (e.g. {@code ...included[0]}) and flat configuration provides as a comma-separated value.
   * Returns {@code null} when the property is not configured at all.
   *
   * <p>{@link org.springframework.boot.context.properties.bind.Binder} is not used here because the
   * declarative property names contain {@code /}, which is not a valid character in a canonical
   * Spring configuration property name.
   */
  @Nullable
  private static List<String> getLoggingListProperty(
      ConfigurableEnvironment environment, String property) {
    String propertyName = getEnvironmentPropertyName(environment, property);
    String value = environment.getProperty(propertyName, String.class);
    if (value != null) {
      return split(value);
    }

    List<String> values = new ArrayList<>();
    boolean configured = false;
    for (int i = 0; ; i++) {
      String item = environment.getProperty(propertyName + "[" + i + "]", String.class);
      if (item == null) {
        break;
      }
      configured = true;
      item = item.trim();
      if (!item.isEmpty()) {
        values.add(item);
      }
    }
    return configured ? values : null;
  }

  private static void addMdcAppender(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    ch.qos.logback.classic.Logger logbackLogger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);
    io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender openTelemetryAppender =
        new io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender();
    initializeMdcAppenderFromProperties(applicationEnvironmentPreparedEvent, openTelemetryAppender);
    openTelemetryAppender.start();
    logbackLogger.addAppender(openTelemetryAppender);
    // move existing appenders under otel mdc appender, so they could observe the added mdc values
    for (Iterator<Appender<ILoggingEvent>> i = logbackLogger.iteratorForAppenders();
        i.hasNext(); ) {
      Appender<ILoggingEvent> appender = i.next();
      if (appender != openTelemetryAppender) {
        openTelemetryAppender.addAppender(appender);
        logbackLogger.detachAppender(appender);
      }
    }
  }

  private static void initializeMdcAppenderFromProperties(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
          openTelemetryAppender) {

    // Reading configuration directly from Spring Environment instead of using declarative
    // configuration because this code runs during ApplicationEnvironmentPreparedEvent, which occurs
    // before the full Spring application context is available. This is the same approach used by
    // org.springframework.boot.context.logging.LoggingApplicationListener.
    // The evaluateBooleanProperty method handles both declarative and non-declarative config by
    // transforming property names when declarative config is detected.
    Boolean addBaggage =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent, "otel.instrumentation.logback-mdc.add-baggage");
    if (addBaggage != null) {
      openTelemetryAppender.setAddBaggage(addBaggage);
    }

    String traceIdKey =
        getLoggingProperty(
            applicationEnvironmentPreparedEvent.getEnvironment(),
            "otel.instrumentation.common.logging.trace-id-key",
            "otel.instrumentation.common.logging.trace-id");
    if (traceIdKey != null) {
      openTelemetryAppender.setTraceIdKey(traceIdKey);
    }

    String spanIdKey =
        getLoggingProperty(
            applicationEnvironmentPreparedEvent.getEnvironment(),
            "otel.instrumentation.common.logging.span-id-key",
            "otel.instrumentation.common.logging.span-id");
    if (spanIdKey != null) {
      openTelemetryAppender.setSpanIdKey(spanIdKey);
    }

    String traceFlagsKey =
        getLoggingProperty(
            applicationEnvironmentPreparedEvent.getEnvironment(),
            "otel.instrumentation.common.logging.trace-flags-key",
            "otel.instrumentation.common.logging.trace-flags");
    if (traceFlagsKey != null) {
      openTelemetryAppender.setTraceFlagsKey(traceFlagsKey);
    }
  }

  @Nullable
  private static String getLoggingProperty(
      ConfigurableEnvironment environment, String newProperty, String oldProperty) {
    String value = getLoggingProperty(environment, newProperty);
    if (value != null) {
      return value;
    }
    value = getLoggingProperty(environment, oldProperty);
    if (value != null) {
      logger.warn(
          "The '{}' property is deprecated and will be removed in 3.0. Use '{}' instead.",
          oldProperty,
          newProperty);
      return value;
    }
    return null;
  }

  @Nullable
  private static String getLoggingProperty(ConfigurableEnvironment environment, String property) {
    return environment.getProperty(getEnvironmentPropertyName(environment, property), String.class);
  }

  /** Evaluates a boolean property, taking into account whether declarative config is in use. */
  @Nullable
  private static Boolean evaluateBooleanProperty(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent, String property) {
    return evaluateBooleanProperty(applicationEnvironmentPreparedEvent.getEnvironment(), property);
  }

  /** Evaluates a boolean property, taking into account whether declarative config is in use. */
  @Nullable
  private static Boolean evaluateBooleanProperty(
      ConfigurableEnvironment environment, String property) {
    return environment.getProperty(
        getEnvironmentPropertyName(environment, property), Boolean.class);
  }

  private static String getEnvironmentPropertyName(
      ConfigurableEnvironment environment, String property) {
    if (EarlyConfig.isDeclarativeConfig(environment)) {
      if (property.startsWith("otel.instrumentation.")) {
        return "otel.instrumentation/development.java."
            + toDeclarativeInstrumentationPropertyName(
                property.substring("otel.instrumentation.".length()));
      } else {
        throw new IllegalStateException(
            "No mapping found for property name: " + property + ". Please report this bug.");
      }
    }
    return property;
  }

  private static String toDeclarativeInstrumentationPropertyName(String instrumentationProperty) {
    StringBuilder declarativeProperty = new StringBuilder();
    boolean nextSegmentIsDevelopment = false;
    for (String segment : instrumentationProperty.split("\\.")) {
      if (segment.equals("experimental")) {
        nextSegmentIsDevelopment = true;
        continue;
      }
      if (declarativeProperty.length() > 0) {
        declarativeProperty.append('.');
      }
      declarativeProperty.append(segment.replace('-', '_'));
      if (nextSegmentIsDevelopment || segment.startsWith("experimental-")) {
        declarativeProperty.append("/development");
        nextSegmentIsDevelopment = false;
      }
    }
    return declarativeProperty.toString();
  }

  private static <T> Optional<T> findAppender(Class<T> appenderClass) {
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return Optional.empty();
    }
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (ch.qos.logback.classic.Logger logbackLogger : loggerContext.getLoggerList()) {
      Iterator<Appender<ILoggingEvent>> appenderIterator = logbackLogger.iteratorForAppenders();
      while (appenderIterator.hasNext()) {
        Appender<ILoggingEvent> appender = appenderIterator.next();
        Optional<T> result = findAppender(appenderClass, appender);
        if (result.isPresent()) {
          return result;
        }
      }
    }
    return Optional.empty();
  }

  private static <T> Optional<T> findAppender(Class<T> appenderClass, Appender<?> appender) {
    if (appenderClass.isInstance(appender)) {
      T openTelemetryAppender = appenderClass.cast(appender);
      return Optional.of(openTelemetryAppender);
    } else if (appender instanceof AppenderAttachable) {
      for (Iterator<? extends Appender<?>> iterator =
              ((AppenderAttachable<?>) appender).iteratorForAppenders();
          iterator.hasNext(); ) {
        Appender<?> childAppender = iterator.next();
        Optional<T> result = findAppender(appenderClass, childAppender);
        if (result.isPresent()) {
          return result;
        }
      }
    }
    return Optional.empty();
  }

  private LogbackAppenderInstaller() {}
}
