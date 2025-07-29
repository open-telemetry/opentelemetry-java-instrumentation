/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.reflect.ClassPath;
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.BootstrapPackagePrefixesHolder;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

// this test is run using
//
// -Dotel.javaagent.exclude-classes=config.exclude.packagename.*,config.exclude.SomeClass,config.exclude.SomeClass$NestedClass
// (see integration-tests.gradle)
class AgentInstrumentationTest {

  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null;
  private static final List<String> BOOTSTRAP_PACKAGE_PREFIXES =
      BootstrapPackagePrefixesHolder.getBoostrapPackagePrefixes();

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void classPathSetUp() throws ClassNotFoundException {
    List<Class<?>> bootstrapClassesIncorrectlyLoaded = new ArrayList<>();
    for (ClassPath.ClassInfo info : getTestClasspath().getAllClasses()) {
      for (String bootstrapPrefix : BOOTSTRAP_PACKAGE_PREFIXES) {
        if (info.getName().startsWith(bootstrapPrefix)) {
          Class<?> bootstrapClass = Class.forName(info.getName());
          ClassLoader loader = bootstrapClass.getClassLoader();
          if (loader != BOOTSTRAP_CLASSLOADER) {
            bootstrapClassesIncorrectlyLoaded.add(bootstrapClass);
          }
        }
      }
    }

    assertThat(bootstrapClassesIncorrectlyLoaded).isEmpty();
  }

  @Test
  void waitingForChildSpansTimesOut() {
    assertThatThrownBy(() -> testing.runWithSpan("parent", () -> testing.waitForTraces(1)))
        .isInstanceOf(AssertionError.class)
        .hasMessage("Error waiting for 1 traces");
  }

  @Test
  void loggingWorks() {
    assertThatNoException()
        .isThrownBy(() -> LoggerFactory.getLogger(AgentInstrumentationTest.class).debug("hello"));
  }

  private static Stream<Arguments> provideExcludedClassTestParameters() {
    return Stream.of(
        Arguments.of(config.SomeClass.class, "SomeClass.run"),
        Arguments.of(config.SomeClass.NestedClass.class, "NestedClass.run"),
        Arguments.of(config.exclude.SomeClass.class, null),
        Arguments.of(config.exclude.SomeClass.NestedClass.class, null),
        Arguments.of(config.exclude.packagename.SomeClass.class, null),
        Arguments.of(config.exclude.packagename.SomeClass.NestedClass.class, null));
  }

  @ParameterizedTest
  @MethodSource("provideExcludedClassTestParameters")
  void excludedClassesAreNotInstrumented(Class<Runnable> subject, String spanName)
      throws Exception {
    testing.runWithSpan("parent", () -> subject.getConstructor().newInstance().run());

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(span -> span.hasName("parent").hasNoParent());
          if (spanName != null) {
            assertions.add(span -> span.hasName(spanName).hasParent(trace.getSpan(0)));
          }
          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  @Test
  void testUnblockedByCompletedSpan() {
    testing.runWithSpan("parent", () -> testing.runWithSpan("child", () -> {}));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> span.hasName("child").hasParent(trace.getSpan(0))));
  }

  private static ClassPath getTestClasspath() {
    ClassLoader testClassLoader = ClasspathUtils.class.getClassLoader();
    if (!(testClassLoader instanceof URLClassLoader)) {
      // java9's system loader does not extend URLClassLoader
      // which breaks Guava ClassPath lookup
      testClassLoader = buildJavaClassPathClassLoader();
    }
    try {
      return ClassPath.from(testClassLoader);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Parse JVM classpath and return ClassLoader containing all classpath entries. Inspired by Guava.
   */
  private static ClassLoader buildJavaClassPathClassLoader() {
    List<URL> urls = new ArrayList<>();
    for (String entry : getClasspath()) {
      try {
        try {
          urls.add(new File(entry).toURI().toURL());
        } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
          urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
        }
      } catch (MalformedURLException e) {
        throw new IllegalStateException(e);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private static String[] getClasspath() {
    return System.getProperty("java.class.path").split(System.getProperty("path.separator"));
  }
}
