/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.Iterator;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

public class LogbackAppenderApplicationListener implements GenericApplicationListener {

  private static final Class<?>[] SOURCE_TYPES = {
    SpringApplication.class, ApplicationContext.class
  };

  private static final Class<?>[] EVENT_TYPES = {ApplicationEnvironmentPreparedEvent.class};

  @Override
  public boolean supportsSourceType(Class<?> sourceType) {
    return isAssignableFrom(sourceType, SOURCE_TYPES);
  }

  @Override
  public boolean supportsEventType(ResolvableType resolvableType) {
    return isAssignableFrom(resolvableType.getRawClass(), EVENT_TYPES);
  }

  private static boolean isAssignableFrom(Class<?> type, Class<?>... supportedTypes) {
    if (type != null) {
      for (Class<?> supportedType : supportedTypes) {
        if (supportedType.isAssignableFrom(type)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (event instanceof ApplicationEnvironmentPreparedEvent // Event for which
        // org.springframework.boot.context.logging.LoggingApplicationListener
        // initializes logging
        && !isOpenTelemetryAppenderAlreadyConfigured()) {
      ch.qos.logback.classic.Logger logger =
          (ch.qos.logback.classic.Logger)
              LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);

      OpenTelemetryAppender appender = new OpenTelemetryAppender();
      appender.start();
      logger.addAppender(appender);
    }
  }

  private static boolean isOpenTelemetryAppenderAlreadyConfigured() {
    ILoggerFactory loggerFactorySpi = LoggerFactory.getILoggerFactory();
    if (!(loggerFactorySpi instanceof LoggerContext)) {
      return false;
    }
    LoggerContext loggerContext = (LoggerContext) loggerFactorySpi;
    for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
      Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
      while (appenderIterator.hasNext()) {
        Appender<ILoggingEvent> appender = appenderIterator.next();
        if (appender instanceof OpenTelemetryAppender) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int getOrder() {
    return LoggingApplicationListener.DEFAULT_ORDER + 1; // To execute this listener just after
    // org.springframework.boot.context.logging.LoggingApplicationListener
  }
}
