/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FILE_PATH;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static io.opentelemetry.semconv.CodeAttributes.CODE_LINE_NUMBER;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LoggerFactory.getLogger("TestLogger");

  static final Resource RESOURCE = Resource.getDefault();
  static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE_INFO =
      InstrumentationScopeInfo.create("TestLogger");

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
    Helper.resetLoggerContext();
  }

  protected abstract InstrumentationExtension getTesting();

  @Test
  void logLoggerContext() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.putProperty("test-property", "test-value");
    try {
      logger.info("log message 1");
      executeAfterLogsExecution();
    } finally {
      Helper.resetLoggerContext();
    }

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(RESOURCE)
                    .hasInstrumentationScope(INSTRUMENTATION_SCOPE_INFO)
                    .hasBody("log message 1")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            CODE_FILE_PATH,
                            AbstractOpenTelemetryAppenderTest.class.getSimpleName() + ".java"),
                        satisfies(CODE_LINE_NUMBER, val -> val.isPositive()),
                        equalTo(
                            CODE_FUNCTION_NAME,
                            AbstractOpenTelemetryAppenderTest.class.getName()
                                + ".logLoggerContext"),
                        equalTo(stringKey("test-property"), "test-value")));
  }
}
