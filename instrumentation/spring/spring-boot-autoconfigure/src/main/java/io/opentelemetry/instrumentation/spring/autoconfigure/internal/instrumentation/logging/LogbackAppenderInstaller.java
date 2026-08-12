/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.EarlyConfig;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

class LogbackAppenderInstaller {
  private static final Logger logger = LoggerFactory.getLogger(LogbackAppenderInstaller.class);
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_MDC_ATTRIBUTES =
      "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes";
  private static final String MDC_ATTRIBUTES_INCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.included";
  private static final String MDC_ATTRIBUTES_EXCLUDED =
      "otel.instrumentation.logback-appender.experimental.mdc-attributes.excluded";

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

    Boolean keyValuePairAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes");
    if (keyValuePairAttributes != null) {
      openTelemetryAppender.setCaptureKeyValuePairAttributes(keyValuePairAttributes);
    }

    Boolean logAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental-log-attributes");
    if (logAttributes != null) {
      openTelemetryAppender.setCaptureExperimentalAttributes(logAttributes);
    }

    Boolean loggerContextAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes");
    if (loggerContextAttributes != null) {
      openTelemetryAppender.setCaptureLoggerContext(loggerContextAttributes);
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

    MdcAttributesConfiguration mdcAttributes =
        getMdcAttributes(applicationEnvironmentPreparedEvent.getEnvironment());
    if (mdcAttributes.configured) {
      openTelemetryAppender.setMdcAttributes(mdcAttributes.selector);
    }
  }

  static MdcAttributesConfiguration getMdcAttributes(ConfigurableEnvironment environment) {
    String includedProperty = getLoggingProperty(environment, MDC_ATTRIBUTES_INCLUDED);
    String excludedProperty = getLoggingProperty(environment, MDC_ATTRIBUTES_EXCLUDED);
    List<String> included = parseList(includedProperty);
    List<String> excluded = parseList(excludedProperty);
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();
    if (!selector.isEmpty()) {
      return MdcAttributesConfiguration.configured(selector);
    }

    boolean newSelectorConfigured = includedProperty != null || excludedProperty != null;
    if (Boolean.TRUE.equals(
        environment.getProperty(
            getEnvironmentPropertyName(environment, "otel.instrumentation.common.v3-preview"),
            Boolean.class))) {
      return newSelectorConfigured
          ? MdcAttributesConfiguration.configured(null)
          : MdcAttributesConfiguration.unconfigured();
    }

    String deprecatedProperty = getLoggingProperty(environment, DEPRECATED_MDC_ATTRIBUTES);
    if (deprecatedProperty != null) {
      if (warnedDeprecatedProperties.add(DEPRECATED_MDC_ATTRIBUTES)) {
        logger.warn(
            "The '{}' property and the equivalent declarative configuration property are"
                + " deprecated and will be removed in 3.0. Use '{}' or equivalent declarative"
                + " configuration instead.",
            DEPRECATED_MDC_ATTRIBUTES,
            MDC_ATTRIBUTES_INCLUDED);
      }
      List<String> deprecatedIncluded = parseList(deprecatedProperty);
      return MdcAttributesConfiguration.configured(
          deprecatedIncluded.isEmpty()
              ? null
              : IncludeExclude.builder().setIncluded(deprecatedIncluded).build());
    }

    return newSelectorConfigured
        ? MdcAttributesConfiguration.configured(null)
        : MdcAttributesConfiguration.unconfigured();
  }

  private static List<String> parseList(@Nullable String value) {
    if (value == null) {
      return emptyList();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isEmpty())
        .collect(toList());
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
    ConfigurableEnvironment environment = applicationEnvironmentPreparedEvent.getEnvironment();
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

  static final class MdcAttributesConfiguration {
    private static final MdcAttributesConfiguration UNCONFIGURED =
        new MdcAttributesConfiguration(false, null);

    private final boolean configured;
    @Nullable private final IncludeExclude selector;

    private static MdcAttributesConfiguration configured(@Nullable IncludeExclude selector) {
      return new MdcAttributesConfiguration(true, selector);
    }

    private static MdcAttributesConfiguration unconfigured() {
      return UNCONFIGURED;
    }

    private MdcAttributesConfiguration(boolean configured, @Nullable IncludeExclude selector) {
      this.configured = configured;
      this.selector = selector;
    }
  }

  private LogbackAppenderInstaller() {}
}
