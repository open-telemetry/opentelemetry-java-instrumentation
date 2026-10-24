/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LogbackAppenderWarningTest {

  private final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
  private final Map<Logger, List<Appender<ILoggingEvent>>> originalAppenders =
      new LinkedHashMap<>();
  private final List<Status> warnings = new ArrayList<>();
  private final StatusListener statusListener =
      status -> {
        if (status.getLevel() == Status.WARN
            && status.getMessage().contains("is downstream of AsyncAppender")) {
          warnings.add(status);
        }
      };

  @BeforeEach
  void setUp() {
    // The installer searches every logger in the global context, not just the root logger.
    for (Logger logger : loggerContext.getLoggerList()) {
      List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
      logger.iteratorForAppenders().forEachRemaining(appenders::add);
      originalAppenders.put(logger, appenders);
      appenders.forEach(logger::detachAppender);
    }
    loggerContext.getStatusManager().add(statusListener);
  }

  @AfterEach
  void tearDown() {
    loggerContext.getStatusManager().remove(statusListener);
    for (Logger logger : loggerContext.getLoggerList()) {
      logger.detachAndStopAllAppenders();
    }
    originalAppenders.forEach((logger, appenders) -> appenders.forEach(logger::addAppender));
  }

  @Test
  void installerWarnsBeforeLoggingForExistingAsyncAppenderOnlyOnceAcrossRestartAndReinstall() {
    OpenTelemetryAppender appender = new OpenTelemetryAppender();
    appender.setName("OTEL");
    appender.setContext(loggerContext);
    appender.start();

    AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setName("ASYNC");
    asyncAppender.setContext(loggerContext);
    asyncAppender.addAppender(appender);
    asyncAppender.start();
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(asyncAppender);
    assertThat(warnings).isEmpty();

    ApplicationEnvironmentPreparedEvent event = environmentPreparedEvent();
    LogbackAppenderInstaller.install(event);

    assertWarning(appender);
    assertThat(appender.isStarted()).isTrue();
    assertThat(asyncAppender.getAppender("OTEL")).isSameAs(appender);

    appender.stop();
    appender.start();
    LogbackAppenderInstaller.install(event);
    OpenTelemetryAppender.install(OpenTelemetry.noop());
    LogbackAppenderInstaller.install(event);

    assertWarning(appender);
  }

  @Test
  void installerDoesNotWarnForAutoAddedDirectAppenderWithSeparateAsyncOutput() {
    ListAppender<ILoggingEvent> output = new ListAppender<>();
    output.setContext(loggerContext);
    output.start();
    AsyncAppender asyncAppender = new AsyncAppender();
    asyncAppender.setName("ASYNC_OUTPUT");
    asyncAppender.setContext(loggerContext);
    asyncAppender.addAppender(output);
    asyncAppender.start();
    Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(asyncAppender);

    ApplicationEnvironmentPreparedEvent event = environmentPreparedEvent();
    LogbackAppenderInstaller.install(event);
    OpenTelemetryAppender.install(OpenTelemetry.noop());
    LogbackAppenderInstaller.install(event);

    List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
    root.iteratorForAppenders().forEachRemaining(appenders::add);
    assertThat(appenders).hasSize(2).contains(asyncAppender);
    assertThat(appenders)
        .filteredOn(OpenTelemetryAppender.class::isInstance)
        .singleElement()
        .satisfies(appender -> assertThat(appender.isStarted()).isTrue());
    assertThat(warnings).isEmpty();
  }

  private void assertWarning(OpenTelemetryAppender appender) {
    assertThat(warnings)
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.getOrigin()).isSameAs(appender);
              assertThat(warning.getMessage())
                  .contains(
                      "OpenTelemetryAppender 'OTEL' is downstream of AsyncAppender 'ASYNC'",
                      "Attach OpenTelemetryAppender directly",
                      "SDK batch export");
            });
  }

  private static ApplicationEnvironmentPreparedEvent environmentPreparedEvent() {
    StandardEnvironment environment = new StandardEnvironment();
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("otel.instrumentation.logback-mdc.enabled", false);
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return new ApplicationEnvironmentPreparedEvent(
        new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment);
  }
}
