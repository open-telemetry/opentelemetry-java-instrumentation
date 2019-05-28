package datadog.trace.instrumentation.glassfish4

import com.sun.enterprise.v3.server.APIClassLoaderServiceImpl
import datadog.trace.agent.test.AgentTestRunner

class GlassfishInstrumentationTest extends AgentTestRunner {

  def "classes not related to datadog can be black-listed"() {
    setup:
    def cli = new APIClassLoaderServiceImpl()
    def cl = cli.getApiClassLoader()
    cl.triggerAddToBlackList('com.some.Entity')
//    def rf = ReflectionFactory.reflectionFactory
//    def clazz = Class.forName('com.sun.enterprise.v3.server.APIClassLoaderServiceImpl$APIClassLoader')
//    def objDef = clazz.getDeclaredConstructor(ClassLoader.class, ClassLoader.class)
//    def intConstr = rf.newConstructorForSerialization(clazz, objDef)
//    def instance = clazz.cast(intConstr.newInstance())

    expect:
    'com.some.Entity' in cl.blacklist
  }

  def "classes related to datadog are not black-listed"() {
    setup:
    def cli = new APIClassLoaderServiceImpl()
    def cl = cli.getApiClassLoader()
    cl.triggerAddToBlackList('io.opentracing.some.Entity')

    expect:
    !('io.opentracing.some.Entity' in cl.blacklist)
    '__datadog_no_blacklist.io.opentracing.some.Entity' in cl.blacklist
  }
}
