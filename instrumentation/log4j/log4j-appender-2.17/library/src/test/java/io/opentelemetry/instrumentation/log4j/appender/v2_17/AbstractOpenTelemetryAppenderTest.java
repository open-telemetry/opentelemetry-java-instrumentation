/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFileAndLineAssertions;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LogManager.getLogger("TestLogger");

  static Resource resource;
  static InstrumentationScopeInfo instrumentationScopeInfo;

  void executeAfterLogsExecution() {}

  @BeforeAll
  static void setupAll() {
    resource = Resource.getDefault();
    instrumentationScopeInfo = InstrumentationScopeInfo.create("TestLogger");
  }

  static void generalBeforeEachSetup() {
    ThreadContext.clearAll();
  }

  @AfterAll
  static void cleanupAll() {
    // This is to make sure that other test classes don't have issues with the logger provider set
    OpenTelemetryAppender.install(null);
  }

  protected abstract InstrumentationExtension getTesting();

  @Test
  void initializeWithBuilder() {
    OpenTelemetryAppender appender =
        OpenTelemetryAppender.builder()
            .setName("OpenTelemetryAppender")
            .setOpenTelemetry(getTesting().getOpenTelemetry())
            .build();
    appender.start();

    appender.append(
        Log4jLogEvent.newBuilder()
            .setMessage(new FormattedMessage("log message 1", (Object) null))
            .build());

    executeAfterLogsExecution();

    getTesting().waitAndAssertLogRecords(logRecord -> logRecord.hasBody("log message 1"));
  }

  protected static List<AttributeAssertion> addLocationAttributes(
      Class<?> testClass, String methodName, AttributeAssertion... assertions) {
    String selector = System.getProperty("Log4j2.contextSelector");
    boolean async = selector != null && selector.endsWith("AsyncLoggerContextSelector");
    if (async && !Boolean.getBoolean("testLatestDeps")) {
      // source info is not available by default when async logger is used in non latest dep tests
      return Arrays.asList(assertions);
    }

    List<AttributeAssertion> result = new ArrayList<>(Arrays.asList(assertions));
    result.addAll(codeFunctionAssertions(testClass, methodName));
    result.addAll(codeFileAndLineAssertions(testClass.getSimpleName() + ".java"));
    return result;
  }
}
