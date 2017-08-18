package com.datadoghq.trace.agent

import com.datadoghq.trace.resolver.FactoryUtils
import com.fasterxml.jackson.core.type.TypeReference
import spock.lang.Specification

class InstrumentationCheckerTest extends Specification {
  Map<String, List<Map<String, String>>> rules =
    FactoryUtils.loadConfigFromResource("supported-version-test", new TypeReference<Map<String, List<InstrumentationChecker.ArtifactSupport>>>() {
    })
  Map<String, String> frameworks = [
    "artifact-1": "1.2.3.1232",
    "artifact-2": "4.y.z",
    "artifact-3": "5.123-1"
  ]

  def checker = new InstrumentationChecker(rules, frameworks)

  def "test rules"() {
    setup:
    def rules = InstrumentationChecker.getUnsupportedRules(java.lang.ClassLoader.getSystemClassLoader())

    expect:
    rules.size() == 3
    rules.sort() == ["unsupportedRuleOne", "unsupportedRuleThree", "unsupportedRuleTwo"]
  }

  static class DemoClass1 {}

  static class DemoClass2 {}

  static class DemoClass3 {}
}
