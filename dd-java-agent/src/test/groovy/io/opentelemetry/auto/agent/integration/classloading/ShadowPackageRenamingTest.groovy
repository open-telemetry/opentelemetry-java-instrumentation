package io.opentelemetry.auto.agent.integration.classloading

import com.google.common.collect.MapMaker
import com.google.common.reflect.ClassPath
import io.opentelemetry.auto.agent.test.IntegrationTestUtils
import spock.lang.Specification

class ShadowPackageRenamingTest extends Specification {
  def "agent dependencies renamed"() {
    setup:
    final Class<?> ddClass =
      IntegrationTestUtils.getAgentClassLoader()
        .loadClass("io.opentelemetry.auto.agent.tooling.AgentInstaller")
    final URL userGuava =
      MapMaker.getProtectionDomain().getCodeSource().getLocation()
    final URL agentGuavaDep =
      ddClass
        .getClassLoader()
        .loadClass("com.google.common.collect.MapMaker")
        .getProtectionDomain()
        .getCodeSource()
        .getLocation()
    final URL agentSource =
      ddClass.getProtectionDomain().getCodeSource().getLocation()

    expect:
    agentSource.getFile() == "/"
    agentSource.getProtocol() == "x-internal-jar"
    agentSource == agentGuavaDep
    agentSource.getFile() != userGuava.getFile()
  }

  def "agent classes not visible"() {
    when:
    ClassLoader.getSystemClassLoader().loadClass("io.opentelemetry.auto.agent.tooling.AgentInstaller")
    then:
    thrown ClassNotFoundException
  }

  def "agent jar contains no bootstrap classes"() {
    setup:
    final ClassPath agentClasspath = ClassPath.from(IntegrationTestUtils.getAgentClassLoader())

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

    expect:
    agentDuplicateClassFile == []
    badBootstrapPrefixes == []
    badAgentPrefixes == []
  }
}
