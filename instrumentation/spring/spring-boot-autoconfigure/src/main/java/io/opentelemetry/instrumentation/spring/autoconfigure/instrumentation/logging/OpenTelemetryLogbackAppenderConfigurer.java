/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.logging;

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.AbstractLoggingSystem;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.ClassUtils;

public class OpenTelemetryLogbackAppenderConfigurer extends AbstractLoggingSystem {
  // implements BeanFactoryInitializationAotProcessor?  {

  public OpenTelemetryLogbackAppenderConfigurer(ClassLoader classLoader) {
    super(classLoader);
  }

  @Override
  public void initialize(
      LoggingInitializationContext initializationContext, String configLocation, LogFile logFile) {

    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            LoggerFactory.getILoggerFactory().getLogger(Logger.ROOT_LOGGER_NAME);

    // To do only if the appender is not in a Logback xml file
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.start();
    logger.addAppender(appender);
  }

  @Override
  protected String[] getStandardConfigLocations() {
    return new String[0];
  }

  @Override
  protected void loadDefaults(
      LoggingInitializationContext initializationContext, LogFile logFile) {}

  @Override
  protected void loadConfiguration(
      LoggingInitializationContext initializationContext, String location, LogFile logFile) {}

  @Order(Ordered.LOWEST_PRECEDENCE)
  public static class Factory implements LoggingSystemFactory {

    private static final boolean PRESENT =
        ClassUtils.isPresent(
            "ch.qos.logback.classic.LoggerContext",
            LogbackLoggingSystem.Factory.class.getClassLoader());

    @Override
    public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
      if (PRESENT) {
        return new OpenTelemetryLogbackAppenderConfigurer(classLoader);
      }
      return null;
    }
  }
}
