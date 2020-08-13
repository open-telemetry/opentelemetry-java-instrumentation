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
import io.opentelemetry.javaagent.OpenTelemetryAgent
import jvmbootstraptest.AgentLoadedChecker
import jvmbootstraptest.MyClassLoaderIsNotBootstrap
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

  // this tests the case where someone adds the contents of opentelemetry-auto.jar by mistake to
  // their application's "uber.jar"
  //
  // the reason this can cause issues is because we locate the agent jar based on the CodeSource of
  // the AgentBootstrap class, and then we add that jar file to the bootstrap class path
  //
  // but if we find the AgentBootstrap class in an uber jar file, and we add that (whole) uber jar
  // file to the bootstrap class loader, that can cause some applications to break, as there's a
  // lot of application and library code that doesn't handle getClassLoader() returning null
  // (e.g. https://github.com/qos-ch/logback/pull/291)
  def "application uber jar should not be added to the bootstrap class loader"() {
    setup:
    def mainClassName = MyClassLoaderIsNotBootstrap.getName()
    def pathToJar = IntegrationTestUtils.createJarWithClasses(mainClassName,
      MyClassLoaderIsNotBootstrap,
      OpenTelemetryAgent).getPath()

    expect:
    IntegrationTestUtils.runOnSeparateJvm(mainClassName
      , "" as String[]
      , "" as String[]
      , [:]
      , pathToJar as String
      , true) == 0

    cleanup:
    new File(pathToJar).delete()
  }
}
