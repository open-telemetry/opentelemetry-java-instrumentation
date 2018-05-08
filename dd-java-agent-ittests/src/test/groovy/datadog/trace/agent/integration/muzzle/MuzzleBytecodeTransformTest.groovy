package datadog.trace.agent.integration.muzzle

import datadog.trace.agent.test.IntegrationTestUtils
import spock.lang.Specification


class MuzzleBytecodeTransformTest extends Specification {

  def "muzzle fields added to all instrumentation"() {
    setup:
    List<Class> unMuzzledClasses = []
    for (final Object instrumenter : ServiceLoader.load(IntegrationTestUtils.getAgentClassLoader().loadClass("datadog.trace.agent.tooling.Instrumenter"), IntegrationTestUtils.getAgentClassLoader())) {
      try {
        instrumenter.getClass().getDeclaredField("instrumentationMuzzle")
      } catch(NoSuchFieldException nsfe) {
        unMuzzledClasses.add(instrumenter.getClass())
      }
    }
    expect:
    unMuzzledClasses == []
  }

}
