/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFileAndLineAssertions;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
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

  static List<AttributeAssertion> addLocationAttributes(
      String methodName, AttributeAssertion... assertions) {
    return addLocationAttributes(AbstractOpenTelemetryAppenderTest.class, methodName, assertions);
  }

  static List<AttributeAssertion> addLocationAttributes(
      Class<?> testClass, String methodName, AttributeAssertion... assertions) {
    List<AttributeAssertion> result = new ArrayList<>(asList(assertions));
    result.addAll(codeFunctionAssertions(testClass, methodName));
    result.addAll(codeFileAndLineAssertions(testClass.getSimpleName() + ".java"));
    return result;
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
                    .hasResource(resource)
                    .hasInstrumentationScope(instrumentationScopeInfo)
                    .hasBody("log message 1")
                    .hasAttributesSatisfyingExactly(
                        addLocationAttributes(
                            "logLoggerContext",
                            equalTo(stringKey("test-property"), "test-value"))));
  }
}
