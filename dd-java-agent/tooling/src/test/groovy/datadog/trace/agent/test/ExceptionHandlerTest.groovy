package datadog.trace.agent.test

import datadog.trace.agent.tooling.ExceptionHandlers
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.dynamic.ClassFileLocator

import static net.bytebuddy.matcher.ElementMatchers.isMethod
import static net.bytebuddy.matcher.ElementMatchers.named

import net.bytebuddy.agent.builder.AgentBuilder
import spock.lang.Specification
import spock.lang.Shared

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.classic.Level

class ExceptionHandlerTest extends Specification {
  @Shared
  ListAppender testAppender = new ListAppender()

  def setupSpec() {
    AgentBuilder builder = new AgentBuilder.Default()
      .disableClassFormatChanges()
      .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      .type(named(getClass().getName()+'$SomeClass'))
      .transform(
      new AgentBuilder.Transformer.ForAdvice()
        .with(new AgentBuilder.LocationStrategy.Simple(ClassFileLocator.ForClassLoader.of(BadAdvice.getClassLoader())))
        .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
        .advice(
        isMethod().and(named("isInstrumented")),
        BadAdvice.getName()))
      .asDecorator()

    ByteBuddyAgent.install()
    builder.installOn(ByteBuddyAgent.getInstrumentation())

    final Logger logger = (Logger) LoggerFactory.getLogger(ExceptionHandlers)
    testAppender.setContext(logger.getLoggerContext())
    logger.addAppender(testAppender)
    testAppender.start()
  }

  def cleanupSpec() {
    testAppender.stop()
  }

  def "exception handler invoked"() {
    setup:
    int initLogEvents = testAppender.list.size()
    expect:
    SomeClass.isInstrumented()
    testAppender.list.size() == initLogEvents + 1
    testAppender.list.get(testAppender.list.size() - 1).getLevel() == Level.DEBUG
    // Make sure the log event came from our error handler.
    // If the log message changes in the future, it's fine to just
    // update the test's hardcoded message
    testAppender.list.get(testAppender.list.size() - 1).getMessage() == "exception in instrumentation"
  }

  def "exception on non-delegating classloader" () {
    setup:
    int initLogEvents = testAppender.list.size()
    URL[] classpath = [ SomeClass.getProtectionDomain().getCodeSource().getLocation(),
                         GroovyObject.getProtectionDomain().getCodeSource().getLocation() ]
    URLClassLoader loader = new URLClassLoader(classpath, (ClassLoader) null)
    when:
    loader.loadClass(LoggerFactory.getName())
    then:
    thrown ClassNotFoundException

    when:
    Class<?> someClazz = loader.loadClass(SomeClass.getName())
    then:
    someClazz.getClassLoader() == loader
    someClazz.getMethod("isInstrumented").invoke(null)
    testAppender.list.size() == initLogEvents
  }

  static class SomeClass {
    static boolean isInstrumented() {
      return false
    }
  }
}
