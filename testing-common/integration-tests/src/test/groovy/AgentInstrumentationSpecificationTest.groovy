/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import com.google.common.reflect.ClassPath
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils
import io.opentelemetry.javaagent.tooling.Constants
import org.slf4j.LoggerFactory

import java.util.concurrent.TimeoutException

// this test is run using
//   -Dotel.javaagent.exclude-classes=config.exclude.packagename.*,config.exclude.SomeClass,config.exclude.SomeClass$NestedClass
// (see integration-tests.gradle)
class AgentInstrumentationSpecificationTest extends AgentInstrumentationSpecification {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null

  def "classpath setup"() {
    setup:
    final List<String> bootstrapClassesIncorrectlyLoaded = []
    for (ClassPath.ClassInfo info : getTestClasspath().getAllClasses()) {
      for (int i = 0; i < Constants.BOOTSTRAP_PACKAGE_PREFIXES.size(); ++i) {
        if (info.getName().startsWith(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i])) {
          Class<?> bootstrapClass = Class.forName(info.getName())
          def loader
          try {
            loader = bootstrapClass.getClassLoader()
          } catch (NoClassDefFoundError e) {
            // some classes in com.google.errorprone.annotations cause groovy to throw
            // java.lang.NoClassDefFoundError: [Ljavax/lang/model/element/Modifier;
            break
          }
          if (loader != BOOTSTRAP_CLASSLOADER) {
            bootstrapClassesIncorrectlyLoaded.add(bootstrapClass)
          }
          break
        }
      }
    }

    expect:
    bootstrapClassesIncorrectlyLoaded == []
  }

  def "waiting for child spans times out"() {
    when:
    runWithSpan("parent") {
      waitForTraces(1)
    }

    then:
    thrown(TimeoutException)
  }

  def "logging works"() {
    when:
    LoggerFactory.getLogger(AgentInstrumentationSpecificationTest).debug("hello")
    then:
    noExceptionThrown()
  }

  def "excluded classes are not instrumented"() {
    when:
    runWithSpan("parent") {
      subject.run()
    }

    then:
    assertTraces(1) {
      trace(0, spanName ? 2 : 1) {
        span(0) {
          name "parent"
        }
        if (spanName) {
          span(1) {
            name spanName
            childOf span(0)
          }
        }
      }
    }

    where:
    subject                                                | spanName
    new config.SomeClass()                                 | "SomeClass.run"
    new config.SomeClass.NestedClass()                     | "NestedClass.run"
    new config.exclude.SomeClass()                         | null
    new config.exclude.SomeClass.NestedClass()             | null
    new config.exclude.packagename.SomeClass()             | null
    new config.exclude.packagename.SomeClass.NestedClass() | null
  }

  def "test unblocked by completed span"() {
    setup:
    runWithSpan("parent") {
      runWithSpan("child") {}
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          hasNoParent()
        }
        span(1) {
          name "child"
          childOf span(0)
        }
      }
    }
  }

  private static ClassPath getTestClasspath() {
    ClassLoader testClassLoader = ClasspathUtils.getClassLoader()
    if (!(testClassLoader instanceof URLClassLoader)) {
      // java9's system loader does not extend URLClassLoader
      // which breaks Guava ClassPath lookup
      testClassLoader = buildJavaClassPathClassLoader()
    }
    try {
      return ClassPath.from(testClassLoader)
    } catch (IOException e) {
      throw new IllegalStateException(e)
    }
  }

  /**
   * Parse JVM classpath and return ClassLoader containing all classpath entries. Inspired by Guava.
   */
  private static ClassLoader buildJavaClassPathClassLoader() {
    List<URL> urls = new ArrayList<>()
    for (String entry : getClasspath()) {
      try {
        try {
          urls.add(new File(entry).toURI().toURL())
        } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
          urls.add(new URL("file", null, new File(entry).getAbsolutePath()))
        }
      } catch (MalformedURLException e) {
        throw new IllegalStateException(e)
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), (ClassLoader) null)
  }

  private static String[] getClasspath() {
    return System.getProperty("java.class.path").split(System.getProperty("path.separator"))
  }
}
