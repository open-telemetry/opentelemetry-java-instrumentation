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
package io.opentelemetry.auto

import io.opentelemetry.auto.test.IntegrationTestUtils
import jvmbootstraptest.AgentLoadedChecker
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(30)
class AgentLoadedIntoBootstrapTest extends Specification {

  def "Agent loads in when separate jvm is launched"() {
    expect:
    IntegrationTestUtils.runOnSeparateJvm(AgentLoadedChecker.getName()
      , "" as String[]
      , "" as String[]
      , [:]
      , true) == 0
  }
}
