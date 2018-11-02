package context

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import net.bytebuddy.utility.JavaModule

import java.lang.ref.WeakReference

import static context.ContextTestInstrumentation.IncorrectCallUsageKeyClass
import static context.ContextTestInstrumentation.IncorrectContextClassUsageKeyClass
import static context.ContextTestInstrumentation.IncorrectKeyClassUsageKeyClass
import static context.ContextTestInstrumentation.KeyClass

class MapBackedProviderTest extends AgentTestRunner {

  def setupSpec() {
    assert new KeyClass().isInstrumented()
  }

  @Override
  boolean onInstrumentationError(
    final String typeName,
    final ClassLoader classLoader,
    final JavaModule module,
    final boolean loaded,
    final Throwable throwable) {
    // Incorrect* classes assert on incorrect api usage. Error expected.
    return !(typeName.startsWith(ContextTestInstrumentation.getName() + "$Incorrect") && throwable.getMessage().startsWith("Incorrect Context Api Usage detected."))
  }

  def "correct api usage stores state in map"() {
    setup:
    KeyClass instance1 = new KeyClass()
    KeyClass instance2 = new KeyClass()

    when:
    instance1.incrementContextCount()

    then:
    instance1.incrementContextCount() == 2
    instance2.incrementContextCount() == 1
  }

  def "get/put test"() {
    setup:
    KeyClass instance1 = new KeyClass()

    when:
    instance1.putContextCount(10)

    then:
    instance1.getContextCount() == 10
  }

  def "backing map should not create strong refs to key class instances"() {
    when:
    KeyClass instance = new KeyClass()
    final int count = instance.incrementContextCount()
    WeakReference<KeyClass> instanceRef = new WeakReference(instance)
    instance = null
    TestUtils.awaitGC(instanceRef)

    then:
    instanceRef.get() == null
    count == 1
  }

  def "incorrect key class usage fails at class load time"() {
    expect:
    !new IncorrectKeyClassUsageKeyClass().isInstrumented()
  }

  def "incorrect context class usage fails at class load time"() {
    expect:
    !new IncorrectContextClassUsageKeyClass().isInstrumented()
  }

  def "incorrect call usage fails at class load time"() {
    expect:
    !new IncorrectCallUsageKeyClass().isInstrumented()
  }
}
