/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.catalina.WebResource
import org.apache.catalina.WebResourceRoot
import org.apache.catalina.loader.ParallelWebappClassLoader

class TomcatClassloadingTest extends AgentInstrumentationSpecification {

  WebResourceRoot resources = Mock(WebResourceRoot) {
    getResource(_) >> Mock(WebResource)
    listResources(_) >> []
    // Looks like 9.x.x needs this one:
    getResources(_) >> []
  }
  ParallelWebappClassLoader classloader = new ParallelWebappClassLoader(null)

  def "tomcat class loading delegates to parent for agent classes"() {
    setup:
    classloader.setResources(resources)
    classloader.init()
    classloader.start()

    expect:
    // If instrumentation didn't work this would blow up with NPE due to incomplete resources mocking
    classloader.loadClass("io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge")
  }
}
