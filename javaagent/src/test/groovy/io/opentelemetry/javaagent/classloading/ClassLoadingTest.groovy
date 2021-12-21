/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.classloading

import io.opentelemetry.javaagent.ClassToInstrument
import io.opentelemetry.javaagent.ClassToInstrumentChild
import io.opentelemetry.javaagent.util.GcUtils
import spock.lang.Specification

import java.lang.ref.WeakReference

import static io.opentelemetry.javaagent.IntegrationTestUtils.createJarWithClasses

class ClassLoadingTest extends Specification {

  final URL[] classpath = [createJarWithClasses(ClassToInstrument, ClassToInstrumentChild)]

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  def "instrument classloader without agent classes"() {
    setup:
    URLClassLoader loader = new URLClassLoader(classpath, (ClassLoader) null)

    when:
    loader.loadClass("io.opentelemetry.javaagent.instrumentation.trace_annotation.TraceAdvice")
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

    GcUtils.awaitGc(ref)

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

  def "make sure that ByteBuddy reads the class bytes only once"() {
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

  def "make sure that ByteBuddy doesn't reuse cached type descriptions between different classloaders"() {
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
}
