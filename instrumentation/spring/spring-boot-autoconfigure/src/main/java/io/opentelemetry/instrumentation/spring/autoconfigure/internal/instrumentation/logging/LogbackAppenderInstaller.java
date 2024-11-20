/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.Iterator;
import java.util.Optional;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;

class LogbackAppenderInstaller {

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

  private static boolean isLogbackAppenderAddable(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    return isAppenderAddable(
        applicationEnvironmentPreparedEvent, "otel.instrumentation.logback-appender.enabled");
  }

  private static boolean isLogbackMdcAppenderAddable(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    return isAppenderAddable(
        applicationEnvironmentPreparedEvent, "otel.instrumentation.logback-mdc.enabled");
  }

  private static boolean isAppenderAddable(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent, String property) {
    boolean otelSdkDisabled =
        evaluateBooleanProperty(applicationEnvironmentPreparedEvent, "otel.sdk.disabled", false);
    boolean logbackInstrumentationEnabled =
        evaluateBooleanProperty(applicationEnvironmentPreparedEvent, property, true);
    return !otelSdkDisabled && logbackInstrumentationEnabled;
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
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);
    OpenTelemetryAppender openTelemetryAppender = new OpenTelemetryAppender();
    initializeOpenTelemetryAppenderFromProperties(
        applicationEnvironmentPreparedEvent, openTelemetryAppender);
    openTelemetryAppender.start();
    logger.addAppender(openTelemetryAppender);
  }

  private static void initializeOpenTelemetryAppenderFromProperties(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      OpenTelemetryAppender openTelemetryAppender) {

    // Implemented in the same way as the
    // org.springframework.boot.context.logging.LoggingApplicationListener, config properties not
    // available
    Boolean codeAttribute =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-code-attributes");
    if (codeAttribute != null) {
      openTelemetryAppender.setCaptureCodeAttributes(codeAttribute.booleanValue());
    }

    Boolean markerAttribute =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-marker-attribute");
    if (markerAttribute != null) {
      openTelemetryAppender.setCaptureMarkerAttribute(markerAttribute.booleanValue());
    }

    Boolean keyValuePairAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-key-value-pair-attributes");
    if (keyValuePairAttributes != null) {
      openTelemetryAppender.setCaptureKeyValuePairAttributes(keyValuePairAttributes.booleanValue());
    }

    Boolean logAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental-log-attributes");
    if (logAttributes != null) {
      openTelemetryAppender.setCaptureExperimentalAttributes(logAttributes.booleanValue());
    }

    Boolean loggerContextAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-logger-context-attributes");
    if (loggerContextAttributes != null) {
      openTelemetryAppender.setCaptureLoggerContext(loggerContextAttributes.booleanValue());
    }

    Boolean captureArguments =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-arguments");
    if (captureArguments != null) {
      openTelemetryAppender.setCaptureArguments(captureArguments.booleanValue());
    }

    Boolean captureLogstashAttributes =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent,
            "otel.instrumentation.logback-appender.experimental.capture-logstash-attributes");
    if (captureLogstashAttributes != null) {
      openTelemetryAppender.setCaptureLogstashAttributes(captureLogstashAttributes.booleanValue());
    }

    String mdcAttributeProperty =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty(
                "otel.instrumentation.logback-appender.experimental.capture-mdc-attributes",
                String.class);
    if (mdcAttributeProperty != null) {
      openTelemetryAppender.setCaptureMdcAttributes(mdcAttributeProperty);
    }
  }

  private static void addMdcAppender(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent) {
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);
    io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender openTelemetryAppender =
        new io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender();
    initializeMdcAppenderFromProperties(applicationEnvironmentPreparedEvent, openTelemetryAppender);
    openTelemetryAppender.start();
    logger.addAppender(openTelemetryAppender);
  }

  private static void initializeMdcAppenderFromProperties(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender
          openTelemetryAppender) {

    // Implemented in the same way as the
    // org.springframework.boot.context.logging.LoggingApplicationListener, config properties not
    // available
    Boolean addBaggage =
        evaluateBooleanProperty(
            applicationEnvironmentPreparedEvent, "otel.instrumentation.logback-mdc.add-baggage");
    if (addBaggage != null) {
      openTelemetryAppender.setAddBaggage(addBaggage);
    }

    String traceIdKey =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty("otel.instrumentation.common.logging.trace-id", String.class);
    if (traceIdKey != null) {
      openTelemetryAppender.setTraceIdKey(traceIdKey);
    }

    String spanIdKey =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty("otel.instrumentation.common.logging.span-id", String.class);
    if (spanIdKey != null) {
      openTelemetryAppender.setSpanIdKey(spanIdKey);
    }

    String traceFlagsKey =
        applicationEnvironmentPreparedEvent
            .getEnvironment()
            .getProperty("otel.instrumentation.common.logging.trace-flags", String.class);
    if (traceFlagsKey != null) {
      openTelemetryAppender.setTraceFlagsKey(traceFlagsKey);
    }
  }

  private static Boolean evaluateBooleanProperty(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent, String property) {
    return applicationEnvironmentPreparedEvent
        .getEnvironment()
        .getProperty(property, Boolean.class);
  }

  private static boolean evaluateBooleanProperty(
      ApplicationEnvironmentPreparedEvent applicationEnvironmentPreparedEvent,
      String property,
      boolean defaultValue) {
    return applicationEnvironmentPreparedEvent
        .getEnvironment()
        .getProperty(property, Boolean.class, defaultValue);
  }

  private static <T> Optional<T> findAppender(Class<T> appenderClass) {
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return Optional.empty();
    }
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
      Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
      while (appenderIterator.hasNext()) {
        Appender<ILoggingEvent> appender = appenderIterator.next();
        if (appenderClass.isInstance(appender)) {
          T openTelemetryAppender = appenderClass.cast(appender);
          return Optional.of(openTelemetryAppender);
        }
      }
    }
    return Optional.empty();
  }

  private LogbackAppenderInstaller() {}
}
