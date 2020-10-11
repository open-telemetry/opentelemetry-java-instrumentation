/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.AgentTestRunner
import org.jboss.weld.environment.se.Weld
import org.jboss.weld.environment.se.WeldContainer
import org.jboss.weld.environment.se.threading.RunnableDecorator

class CDIContainerTest extends AgentTestRunner {

  def "CDI container starts with agent"() {
    given:
    Weld builder = new Weld()
      .disableDiscovery()
      .addDecorator(RunnableDecorator)
      .addBeanClass(TestBean)

    when:
    WeldContainer container = builder.initialize()

    then:
    container.isRunning()

    cleanup:
    container?.shutdown()
  }
}
