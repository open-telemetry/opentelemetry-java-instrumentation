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
        .loadClass("com.google.common.collect.MapMaker")
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
    final ClassPath jmxFetchClasspath = ClassPath.from(IntegrationTestUtils.getJmxFetchClassLoader())

    final ClassPath bootstrapClasspath = ClassPath.from(IntegrationTestUtils.getBootstrapProxy())
    final Set<String> bootstrapClasses = new HashSet<>()
    final String[] bootstrapPrefixes = IntegrationTestUtils.getBootstrapPackagePrefixes()
    final String[] agentPrefixes = IntegrationTestUtils.getAgentPackagePrefixes()
    final List<String> badBootstrapPrefixes = []
    for (ClassPath.ClassInfo info : bootstrapClasspath.getAllClasses()) {
      bootstrapClasses.add(info.getName())
      // make sure all bootstrap classes can be loaded from system
      ClassLoader.getSystemClassLoader().loadClass(info.getName())
      boolean goodPrefix = false
      for (int i = 0; i < bootstrapPrefixes.length; ++i) {
        if (info.getName().startsWith(bootstrapPrefixes[i])) {
          goodPrefix = true
          break
        }
      }
      if (!goodPrefix) {
        badBootstrapPrefixes.add(info.getName())
      }
    }

    final List<ClassPath.ClassInfo> agentDuplicateClassFile = new ArrayList<>()
    final List<String> badAgentPrefixes = []
    for (ClassPath.ClassInfo classInfo : agentClasspath.getAllClasses()) {
      if (bootstrapClasses.contains(classInfo.getName())) {
        agentDuplicateClassFile.add(classInfo)
      }
      boolean goodPrefix = false
      for (int i = 0; i < agentPrefixes.length; ++i) {
        if (classInfo.getName().startsWith(agentPrefixes[i])) {
          goodPrefix = true
          break
        }
      }
      if (!goodPrefix) {
        badAgentPrefixes.add(classInfo.getName())
      }
    }

    final List<ClassPath.ClassInfo> jmxFetchDuplicateClassFile = new ArrayList<>()
    final List<String> badJmxFetchPrefixes = []
    for (ClassPath.ClassInfo classInfo : jmxFetchClasspath.getAllClasses()) {
      if (bootstrapClasses.contains(classInfo.getName())) {
        jmxFetchDuplicateClassFile.add(classInfo)
      }
      boolean goodPrefix = true
      for (int i = 0; i < bootstrapPrefixes.length; ++i) {
        if (classInfo.getName().startsWith(bootstrapPrefixes[i])) {
          goodPrefix = false
          break
        }
      }
      if (!goodPrefix) {
        badJmxFetchPrefixes.add(classInfo.getName())
      }
    }

    expect:
    agentDuplicateClassFile == []
    badBootstrapPrefixes == []
    badAgentPrefixes == []
    badJmxFetchPrefixes == []
  }
}
