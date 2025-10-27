/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeAttributesLogCount;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LoggerFactory.getLogger("TestLogger");

  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");
    // by default LoggerContext contains HOSTNAME property we clear it to start with empty context
    Helper.resetLoggerContext();
  }

  protected abstract InstrumentationExtension getTesting();

  @Test
  void logLoggerContext() {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.putProperty("test-property", "test-value");

    logger.info("log message 1");
    executeAfterLogsExecution();

    getTesting()
        .waitAndAssertLogRecords(
            logRecord ->
                logRecord
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasTotalAttributeCount(codeAttributesLogCount() + 1)
                    .hasAttributesSatisfying(
                        equalTo(AttributeKey.stringKey("test-property"), "test-value")));
  }
}
