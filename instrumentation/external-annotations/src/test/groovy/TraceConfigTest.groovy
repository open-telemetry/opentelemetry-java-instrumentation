/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ConfigUtils
import java.util.concurrent.Callable

class TraceConfigTest extends AgentTestRunner {
  static final PREVIOUS_CONFIG = ConfigUtils.updateConfigAndResetInstrumentation {
    it.setProperty("otel.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
  }

  def cleanupSpec() {
    ConfigUtils.setConfig(PREVIOUS_CONFIG)
  }

  static class ConfigTracedCallable implements Callable<String> {
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }

  def "test configuration based trace"() {
    expect:
    new ConfigTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "ConfigTracedCallable.call"
          attributes {
          }
        }
      }
    }
  }
}
