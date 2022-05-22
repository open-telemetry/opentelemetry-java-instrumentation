/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader
import io.opentelemetry.javaagent.tooling.UnsafeInitializer
import net.bytebuddy.agent.ByteBuddyAgent
import spock.lang.Specification

class UnsafeTest extends Specification {

  def "test generate sun.misc.Unsafe"() {
    setup:
    ByteBuddyAgent.install()
    URL testJarLocation = AgentClassLoader.getProtectionDomain().getCodeSource().getLocation()
    AgentClassLoader loader = new AgentClassLoader(new File(testJarLocation.toURI()), "")
    UnsafeInitializer.initialize(ByteBuddyAgent.getInstrumentation(), loader, false)

    expect:
    loader.loadClass("sun.misc.Unsafe").getClassLoader() == loader
  }
}
