/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFileAndLineAssertions;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.spi.ContextAware;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;

class LogReplayOpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeEach
  void setup() throws Exception {
    // to make sure we start fresh with a new OpenTelemetryAppender for each test
    reloadLoggerConfiguration();
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
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
    } catch (Exception ignored) {
      // logback versions prior to 1.3.0
      ContextInitializer ci = new ContextInitializer(loggerContext);
      URL url = LogReplayOpenTelemetryAppenderTest.class.getResource("/logback-test.xml");
      ContextInitializer.class.getMethod("configureByResource", URL.class).invoke(ci, url);
      // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
      Helper.resetLoggerContext();
    }
  }

  @Override
  void executeAfterLogsExecution() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);
  }

  @Test
  void twoLogs() {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.addAll(codeFunctionAssertions(LogReplayOpenTelemetryAppenderTest.class, "twoLogs"));
    assertions.addAll(
        codeFileAndLineAssertions(
            LogReplayOpenTelemetryAppenderTest.class.getSimpleName() + ".java"));

    logger.info("log message 1");
    logger.info(
        "log message 2"); // Won't be instrumented because cache size is 1 (see logback-test.xml
    // file)

    OpenTelemetryAppender.install(testing.getOpenTelemetry());
    cleanup.deferCleanup(OpenTelemetryAppender::resetForTest);

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasResource(RESOURCE)
                .hasInstrumentationScope(INSTRUMENTATION_SCOPE_INFO)
                .hasBody("log message 1")
                .hasAttributesSatisfyingExactly(assertions));
  }
}
