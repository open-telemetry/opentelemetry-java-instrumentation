package datadog.trace.instrumentation.glassfish4

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.tooling.Constants
import net.bytebuddy.matcher.NameMatcher

class GlassfishInstrumentationTest extends AgentTestRunner {

  def "test type matches the correct class loader"() {
    setup:
    def matchingType = new GlassfishInstrumentation().typeMatcher()

    expect:
    matchingType instanceof NameMatcher
    matchingType.toString() == 'name(equals(com.sun.enterprise.v3.server.APIClassLoaderServiceImpl$APIClassLoader))'
  }

  def "test correct classes are added to the helpers"() {
    setup:
    def helpers = new GlassfishInstrumentation().helperClassNames()

    expect:
    Constants.class.getName() in helpers
  }

  def "test the advice is registered for the 'addToBlackList' method"() {
    setup:
    def transformers = new GlassfishInstrumentation().transformers()
    def transformer = transformers.entrySet()[0]

    expect:
    transformer.key.toString() == '((isMethod() and name(equals(addToBlackList))) and hasParameter(ofSize(1)))'
  }
}
