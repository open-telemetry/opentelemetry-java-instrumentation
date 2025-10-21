/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.classloading;

import static io.opentelemetry.javaagent.IntegrationTestUtils.createJarWithClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.javaagent.ClassToInstrument;
import io.opentelemetry.javaagent.ClassToInstrumentChild;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassLoadingTest {

  private URL[] classpath;

  @BeforeEach
  void setUp() throws Exception {
    classpath =
        new URL[] {createJarWithClasses(ClassToInstrument.class, ClassToInstrumentChild.class)};
  }

  /** Assert that we can instrument classloaders which cannot resolve agent advice classes. */
  @Test
  void instrumentClassloaderWithoutAgentClasses() throws Exception {
    URLClassLoader loader = new URLClassLoader(classpath, null);

    assertThatThrownBy(
            () ->
                loader.loadClass(
                    "io.opentelemetry.javaagent.instrumentation.trace_annotation.TraceAdvice"))
        .isInstanceOf(ClassNotFoundException.class);

    Class<?> instrumentedClass = loader.loadClass(ClassToInstrument.class.getName());
    assertThat(instrumentedClass.getClassLoader()).isEqualTo(loader);
  }

  @Test
  void makeSureByteBuddyDoesNotHoldStrongReferencesToClassLoader() throws Exception {
    URLClassLoader loader = new URLClassLoader(classpath, null);
    WeakReference<URLClassLoader> ref = new WeakReference<>(loader);

    loader.loadClass(ClassToInstrument.class.getName());
    //noinspection UnusedAssignment
    loader = null;

    GcUtils.awaitGc(ref, Duration.ofSeconds(10));

    assertThat(ref.get()).isNull();
  }

  static class CountingClassLoader extends URLClassLoader {
    public int count = 0;

    CountingClassLoader(URL[] urls) {
      super(urls, null);
    }

    @Override
    public URL getResource(String name) {
      count++;
      return super.getResource(name);
    }
  }

  @Test
  void makeSureThatByteBuddyReadsTheClassBytesOnlyOnce() throws Exception {
    try (CountingClassLoader loader = new CountingClassLoader(classpath)) {
      loader.loadClass(ClassToInstrument.class.getName());
      int countAfterFirstLoad = loader.count;
      loader.loadClass(ClassToInstrumentChild.class.getName());

      // ClassToInstrumentChild won't cause an additional getResource() because its TypeDescription
      // is created from transformation bytes.
      assertThat(loader.count).isPositive().isEqualTo(countAfterFirstLoad);
    }
  }

  @Test
  void makeSureThatByteBuddyDoesntReuseCachedTypeDescriptionsBetweenDifferentClassloaders()
      throws Exception {
    try (CountingClassLoader loader1 = new CountingClassLoader(classpath);
        CountingClassLoader loader2 = new CountingClassLoader(classpath)) {

      loader1.loadClass(ClassToInstrument.class.getName());
      loader2.loadClass(ClassToInstrument.class.getName());

      assertThat(loader1.count).isPositive();
      assertThat(loader2.count).isPositive();
      assertThat(loader1.count).isEqualTo(loader2.count);
    }
  }
}
