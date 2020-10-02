/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.instrumentation.api.config.Config
import java.util.concurrent.Callable

class TraceConfigTest extends AgentTestRunner {
  static final Config previousConfig = ConfigUtils.updateConfigAndResetInstrumentation {
    it.setProperty("otel.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
  }

  def cleanupSpec() {
    ConfigUtils.setConfig(previousConfig)
  }

  class ConfigTracedCallable implements Callable<String> {
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
