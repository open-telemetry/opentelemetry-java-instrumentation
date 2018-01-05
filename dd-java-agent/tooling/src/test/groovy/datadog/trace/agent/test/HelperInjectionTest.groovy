package datadog.trace.agent.test

import datadog.trace.agent.tooling.HelperInjector
import datadog.trace.agent.tooling.Utils
import spock.lang.Specification

import java.lang.reflect.Method

class HelperInjectionTest extends Specification {

  def "helpers injected to non-delegating classloader"() {
    setup:
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'
    HelperInjector injector = new HelperInjector(helperClassName)
    URLClassLoader emptyLoader = new URLClassLoader(new URL[0], (ClassLoader) null)

    when:
    emptyLoader.loadClass(helperClassName)
    then:
    thrown ClassNotFoundException

    when:
    injector.transform(null, null, emptyLoader, null)
    emptyLoader.loadClass(helperClassName)
    then:
    isClassLoaded(helperClassName, emptyLoader)
    // injecting into emptyLoader should not load on agent's classloader
    !isClassLoaded(helperClassName, Utils.getAgentClassLoader())

    cleanup:
    emptyLoader?.close()
  }

  private static boolean isClassLoaded(String className, ClassLoader classLoader) {
    final Method findLoadedClassMethod = ClassLoader.getDeclaredMethod("findLoadedClass", String)
    try {
      findLoadedClassMethod.setAccessible(true)
      Class<?> loadedClass = (Class<?>) findLoadedClassMethod.invoke(classLoader, className)
      return null != loadedClass && loadedClass.getClassLoader() == classLoader
    } catch (Exception e) {
      throw new IllegalStateException(e)
    } finally {
      findLoadedClassMethod.setAccessible(false)
    }
  }
}
