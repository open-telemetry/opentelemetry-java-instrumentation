/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.auto

import io.opentelemetry.instrumentation.test.IntegrationTestUtils
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
