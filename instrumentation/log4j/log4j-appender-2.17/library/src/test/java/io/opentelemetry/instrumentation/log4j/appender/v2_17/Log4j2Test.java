/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class Log4j2Test extends AbstractLog4j2Test {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<AttributeAssertion> addCodeLocationAttributes(String methodName) {
    // For library tests, AsyncLogger can't capture code location in older versions
    String selector = System.getProperty("Log4j2.contextSelector");
    boolean async = selector != null && selector.endsWith("AsyncLoggerContextSelector");
    if (async && !testLatestDeps()) {
      // source info is not available by default when async logger is used in non latest dep tests
      return new ArrayList<>();
    }

    return super.addCodeLocationAttributes(methodName);
  }
}
