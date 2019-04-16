package datadog.trace.agent.integration.classloading

import datadog.test.ClassToInstrument
import datadog.test.ClassToInstrumentChild
import datadog.trace.api.Trace
import datadog.trace.util.gc.GCUtils
import spock.lang.Specification
import spock.lang.Timeout

import java.lang.ref.WeakReference

import static datadog.trace.agent.test.IntegrationTestUtils.createJarWithClasses

@Timeout(10)
class ClassLoadingTest extends Specification {

  final URL[] classpath = [createJarWithClasses(ClassToInstrument, ClassToInstrumentChild, Trace)]

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  def "instrument classloader without agent classes"() {
    setup:
    URLClassLoader loader = new URLClassLoader(classpath, (ClassLoader) null)

    when:
    loader.loadClass("datadog.agent.TracingAgent")
    then:
    thrown ClassNotFoundException

    when:
    Class<?> instrumentedClass = loader.loadClass(ClassToInstrument.getName())
    then:
    instrumentedClass.getClassLoader() == loader
  }

  def "make sure ByteBuddy does not hold strong references to ClassLoader"() {
    setup:
    URLClassLoader loader = new URLClassLoader(classpath, (ClassLoader) null)
    WeakReference<URLClassLoader> ref = new WeakReference<>(loader)

    when:
    loader.loadClass(ClassToInstrument.getName())
    loader = null

    GCUtils.awaitGC(ref)

    then:
    null == ref.get()
  }

  // We are doing this because Groovy cannot properly resolve constructor argument types in anonymous classes
  static class CountingClassLoader extends URLClassLoader {
    public int count = 0

    CountingClassLoader(URL[] urls) {
      super(urls, (ClassLoader) null)
    }

    @Override
    URL getResource(String name) {
      count++
      return super.getResource(name)
    }
  }

  def "make sure that ByteBuddy reads classes's bytes only once"() {
    setup:
    CountingClassLoader loader = new CountingClassLoader(classpath)

    when:
    //loader.loadClass("aaa")
    loader.loadClass(ClassToInstrument.getName())
    int countAfterFirstLoad = loader.count
    loader.loadClass(ClassToInstrumentChild.getName())

    then:
    // ClassToInstrumentChild won't cause an additional getResource() because its TypeDescription is created from transformation bytes.
    loader.count > 0
    loader.count == countAfterFirstLoad
  }

  def "make sure that ByteBuddy doesn't resue cached type descriptions between different classloaders"() {
    setup:
    CountingClassLoader loader1 = new CountingClassLoader(classpath)
    CountingClassLoader loader2 = new CountingClassLoader(classpath)

    when:
    loader1.loadClass(ClassToInstrument.getName())
    loader2.loadClass(ClassToInstrument.getName())

    then:
    loader1.count > 0
    loader2.count > 0
    loader1.count == loader2.count
  }

  def "can find classes but not resources loaded onto the bootstrap classpath"() {
    expect:
    Class.forName(name) != null

    // Resources from bootstrap injected jars can't be loaded.
    // https://github.com/raphw/byte-buddy/pull/496
    if (onTestClasspath) {
      assert ClassLoader.getSystemClassLoader().getResource(resource) != null
    } else {
      assert ClassLoader.getSystemClassLoader().getResource(resource) == null
    }


    where:
    name                      | onTestClasspath
    "datadog.trace.api.Trace" | true
    // This test case fails on ibm j9.  Perhaps this rule only applies to OpenJdk based jvms?
//    "datadog.trace.bootstrap.instrumentation.java.concurrent.State" | false
    resource = name.replace(".", "/") + ".class"
  }
}
