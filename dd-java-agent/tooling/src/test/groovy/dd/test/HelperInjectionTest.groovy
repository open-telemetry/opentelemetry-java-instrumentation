package dd.test

import static dd.test.TestUtils.createJarWithClasses

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
    URL[] classpath = [createJarWithClasses(TestInstrumentation.ClassToInstrument)]
    URLClassLoader classloader = new URLClassLoader(classpath, (ClassLoader)null)

    when:
    classloader.loadClass(TestInstrumentation.HelperClass.getName())
    then:
    thrown ClassNotFoundException

    when:
    Class<?> instrumentedClass = classloader.loadClass(TestInstrumentation.ClassToInstrument.getName())
    Method instrumentedMethod = instrumentedClass.getMethod("isInstrumented")
    then:
    instrumentedMethod.invoke(null)

    when:
    classloader.loadClass(TestInstrumentation.HelperClass.getName())
    then:
    noExceptionThrown()

    cleanup:
    classloader?.close()
  }
}
