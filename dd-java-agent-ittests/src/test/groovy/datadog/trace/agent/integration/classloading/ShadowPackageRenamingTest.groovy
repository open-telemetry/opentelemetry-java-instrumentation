package datadog.trace.agent.integration.classloading

import com.google.common.collect.MapMaker
import com.google.common.reflect.ClassPath
import datadog.trace.agent.test.IntegrationTestUtils
import io.opentracing.util.GlobalTracer
import spock.lang.Specification

import java.lang.reflect.Field


class ShadowPackageRenamingTest extends Specification {
  def "agent dependencies renamed"() {
    setup:
    final Class<?> ddClass =
      IntegrationTestUtils.getAgentClassLoader()
        .loadClass("datadog.trace.agent.tooling.AgentInstaller")
    final String userGuava =
      MapMaker.getProtectionDomain().getCodeSource().getLocation().getFile()
    final String agentGuavaDep =
      ddClass
        .getClassLoader()
        .loadClass("datadog.trace.agent.deps.google.common.collect.MapMaker")
        .getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .getFile()
    final String agentSource =
      ddClass.getProtectionDomain().getCodeSource().getLocation().getFile()

    expect:
    agentSource.matches(".*/agent-tooling-and-instrumentation[^/]*.jar")
    agentSource == agentGuavaDep
    agentSource != userGuava
  }

  def "java getLogger rewritten to safe logger"() {
    setup:
    Field logField = GlobalTracer.getDeclaredField("LOGGER")
    logField.setAccessible(true)
    Object logger = logField.get(null)

    expect:
    !logger.getClass().getName().startsWith("java.util.logging")

    cleanup:
    logField?.setAccessible(false)
  }

  def "agent classes not visible"() {
    when:
    ClassLoader.getSystemClassLoader().loadClass("datadog.trace.agent.tooling.AgentInstaller")
    then:
    thrown ClassNotFoundException
  }

  def "agent jar contains no bootstrap classes"() {
   setup:
   final ClassPath agentClasspath = ClassPath.from(IntegrationTestUtils.getAgentClassLoader())

   final ClassPath bootstrapClasspath = ClassPath.from(IntegrationTestUtils.getBootstrapResourceLocator())
   final Set<String> bootstrapClasses = new HashSet<>()
   for (ClassPath.ClassInfo info : bootstrapClasspath.getAllClasses()) {
     bootstrapClasses.add(info.getName())
     // make sure all bootstrap classes can be loaded from system
     ClassLoader.getSystemClassLoader().loadClass(info.getName())
   }

   final List<ClassPath.ClassInfo> duplicateClassFile = new ArrayList<>()
   for (ClassPath.ClassInfo classInfo : agentClasspath.getAllClasses()) {
     if (bootstrapClasses.contains(classInfo.getName())) {
       duplicateClassFile.add(classInfo)
     }
   }

   expect:
   duplicateClassFile.size() == 0
  }
}
