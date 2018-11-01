package context

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import net.bytebuddy.utility.JavaModule

import java.lang.ref.WeakReference

class MapBackedProviderTest extends AgentTestRunner {

  def setupSpec() {
    assert new UserClass1().isInstrumented()
  }

  @Override
  boolean onInstrumentationError(
    final String typeName,
    final ClassLoader classLoader,
    final JavaModule module,
    final boolean loaded,
    final Throwable throwable) {
    // UserClass2 asserts on incorrect api usage. Error expected.
    return !(typeName.equals(UserClass2.getName()) && throwable.getMessage().startsWith("Incorrect Context Api Usage detected."))
  }

  def "correct api usage stores state in map"() {
    when:
    UserClass1 instance1 = new UserClass1()
    UserClass1 instance2 = new UserClass1()
    instance1.incrementContextCount()

    then:
    instance1.incrementContextCount() == 2
    instance2.incrementContextCount() == 1
  }

  def "backing map should not create strong refs to user instances"() {
    when:
    UserClass1 instance = new UserClass1()
    final int count = instance.incrementContextCount()
    WeakReference<UserClass1> instanceRef = new WeakReference(instance)
    instance = null
    TestUtils.awaitGC(instanceRef)

    then:
    instanceRef.get() == null
    count == 1
  }

  def "incorrect api usage fails at class load time"() {
    expect:
    !new UserClass2().isInstrumented()
  }
}
