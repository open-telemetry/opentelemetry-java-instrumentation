/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.ConfigUtils

import java.util.concurrent.Callable

class TraceConfigTest extends AgentTestRunner {

  static {
    ConfigUtils.updateConfig {
      System.setProperty("ota.trace.methods", "package.ClassName[method1,method2];${ConfigTracedCallable.name}[call]")
    }
  }

  def specCleanup() {
    ConfigUtils.updateConfig {
      System.clearProperty("ota.trace.methods")
    }
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
          operationName "ConfigTracedCallable.call"
          tags {
          }
        }
      }
    }
  }
}
