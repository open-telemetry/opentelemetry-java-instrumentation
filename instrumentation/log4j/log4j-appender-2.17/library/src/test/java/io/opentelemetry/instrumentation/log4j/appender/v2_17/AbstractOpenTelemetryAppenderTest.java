/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFileAndLineAssertions;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.FormattedMessage;
import org.junit.jupiter.api.Test;

abstract class AbstractOpenTelemetryAppenderTest {

  static final Logger logger = LogManager.getLogger("TestLogger");

  static final Resource RESOURCE = Resource.getDefault();
  static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE_INFO =
      InstrumentationScopeInfo.create("TestLogger");

  void executeAfterLogsExecution() {}

  static void generalBeforeEachSetup() {
    ThreadContext.clearAll();
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
    if (async && !testLatestDeps()) {
      // source info is not available by default when async logger is used in non latest dep tests
      return asList(assertions);
    }

    List<AttributeAssertion> result = new ArrayList<>(asList(assertions));
    result.addAll(codeFunctionAssertions(testClass, methodName));
    result.addAll(codeFileAndLineAssertions(testClass.getSimpleName() + ".java"));
    return result;
  }
}
