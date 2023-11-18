/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.spi.ContextAware;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LogReplayOpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @BeforeEach
  void setup() throws Exception {
    generalBeforeEachSetup();
    // to make sure we start fresh with a new OpenTelemetryAppender for each test
    reloadLoggerConfiguration();
  }

  private static void reloadLoggerConfiguration() throws Exception {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.reset();
    try {
      Class<?> configuratorClass =
          Class.forName("ch.qos.logback.classic.util.DefaultJoranConfigurator");
      Object configurator = configuratorClass.getConstructor().newInstance();
      ((ContextAware) configurator).setContext(loggerContext);
      configuratorClass
          .getMethod("configure", LoggerContext.class)
          .invoke(configurator, loggerContext);
    } catch (Exception e) {
      // logback versions prior to 1.3.0
      ContextInitializer ci = new ContextInitializer(loggerContext);
      URL url = LogReplayOpenTelemetryAppenderTest.class.getResource("/logback-test.xml");
      ContextInitializer.class.getMethod("configureByResource", URL.class).invoke(ci, url);
      // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
      resetLoggerContext();
    }
  }

  @Override
  void executeAfterLogsExecution() {
    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  @Test
  void twoLogs() {
    logger.info("log message 1");
    logger.info(
        "log message 2"); // Won't be instrumented because cache size is 1 (see logback-test.xml
    // file)

    OpenTelemetryAppender.install(openTelemetrySdk);

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    LogRecordData logData = logDataList.get(0);
    OpenTelemetryAssertions.assertThat(logData)
        .hasResource(resource)
        .hasInstrumentationScope(instrumentationScopeInfo)
        .hasBody("log message 1")
        .hasTotalAttributeCount(4);
  }
}
