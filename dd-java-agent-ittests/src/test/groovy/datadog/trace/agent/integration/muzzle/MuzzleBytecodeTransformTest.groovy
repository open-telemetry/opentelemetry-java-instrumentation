package datadog.trace.agent.integration.muzzle

import datadog.trace.agent.test.IntegrationTestUtils
import java.lang.reflect.Field
import java.lang.reflect.Method
import spock.lang.Specification


class MuzzleBytecodeTransformTest extends Specification {

  def "muzzle fields added to all instrumentation"() {
    setup:
    List<Class> unMuzzledClasses = []
    List<Class> nonLazyFields = []
    List<Class> unInitFields = []
    for (final Object instrumenter : ServiceLoader.load(IntegrationTestUtils.getAgentClassLoader().loadClass("datadog.trace.agent.tooling.Instrumenter"), IntegrationTestUtils.getAgentClassLoader())) {
      Field f
      Method m
      try {
        f = instrumenter.getClass().getDeclaredField("instrumentationMuzzle")
        f.setAccessible(true)
        if (f.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass())
        }
        m = instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle")
        m.setAccessible(true)
        m.invoke(instrumenter)
        if (f.get(instrumenter) == null) {
          unInitFields.add(instrumenter.getClass())
        }
      } catch(NoSuchFieldException | NoSuchMethodException e) {
        unMuzzledClasses.add(instrumenter.getClass())
      } finally {
        if (null != f) {
          f.setAccessible(false)
        }
        if (null != m) {
          m.setAccessible(false)
        }
      }
    }
    expect:
    unMuzzledClasses == []
    nonLazyFields == []
    unInitFields == []
  }

}
