package dd.test

import static dd.test.TestUtils.createJarWithClasses

import dd.trace.DDAdvice
import dd.trace.HelperInjector
import java.lang.reflect.Method
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import spock.lang.Specification

import static net.bytebuddy.matcher.ElementMatchers.*

class HelperInjectionTest extends Specification {
  def setupSpec() {
    AgentBuilder builder =
      new AgentBuilder.Default()
        .disableClassFormatChanges()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .ignore(nameStartsWith("dd.inst"))

    builder = new TestInstrumentation().instrument(builder)

    builder.installOn(ByteBuddyAgent.install())
  }

  def "helpers injected to non-delegating classloader"() {
    setup:
    String helperClassName = TestInstrumentation.getName() + '$HelperClass'
    String instrumentationClassName = TestInstrumentation.getName() + '$ClassToInstrument'
    HelperInjector injector = new HelperInjector(TestInstrumentation.getName() + '$HelperClass')
    URLClassLoader emptyLoader = new URLClassLoader(new URL[0], (ClassLoader)null)
    injector.transform(null, null, emptyLoader, null)
    // injecting into emptyLoader should not load on agent's classloader
    assert !TestUtils.isClassLoaded(helperClassName, DDAdvice.getAgentClassLoader())
    assert TestUtils.isClassLoaded(helperClassName, emptyLoader)

    URL[] classpath = [createJarWithClasses(instrumentationClassName)]
    URLClassLoader classloader = new URLClassLoader(classpath, (ClassLoader)null)

    when:
    classloader.loadClass(helperClassName)
    then:
    thrown ClassNotFoundException

    when:
    Class<?> instrumentedClass = classloader.loadClass(instrumentationClassName)
    Method instrumentedMethod = instrumentedClass.getMethod("isInstrumented")
    then:
    instrumentedMethod.invoke(null)

    when:
    classloader.loadClass(helperClassName)
    then:
    noExceptionThrown()

    cleanup:
    classloader?.close()
    emptyLoader?.close()
  }
}
