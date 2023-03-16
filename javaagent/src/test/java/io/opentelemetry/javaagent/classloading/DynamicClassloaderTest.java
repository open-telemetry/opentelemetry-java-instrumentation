/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.classloading;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.javaagent.ClassToInstrument;
import io.opentelemetry.javaagent.util.GcUtils;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicClassloaderTest {

  private static final Logger logger = LoggerFactory.getLogger(DynamicClassloaderTest.class);

  @Ignore("This test takes a long time.  Not great for CI.")
  @Test
  void testDynamicGeneratedClassLoaderMemory()
      throws InterruptedException, ClassNotFoundException, IOException {
    URL resource = ClassToInstrument.class.getProtectionDomain().getCodeSource().getLocation();
    URL[] urls = new URL[] {resource};
    WeakReference<Object> ref = new WeakReference<>(new Object());
    GcUtils.awaitGc(ref);
    long startingHeapBytes = Runtime.getRuntime().totalMemory();
    Class<?> loadedClass = null;
    for (int i = 0; i < 1_000_000; i++) {
      try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {
        loadedClass = classLoader.loadClass(ClassToInstrument.class.getName());
        assertNotNull(loadedClass);
      }
      if (i % 100_000 == 0) {
        logger.info(Integer.toString(i));
      }
    }
    ref = new WeakReference<>(loadedClass);
    loadedClass = null;
    GcUtils.awaitGc(ref);
    assertNull(ref.get());
    long endingHeapBytes = Runtime.getRuntime().totalMemory();
    // unconstrained cache size with many keys->null references.
    assertTrue(
        endingHeapBytes - startingHeapBytes < 15_000_000,
        (endingHeapBytes - startingHeapBytes) / 1_000_000 + "MB is more than 15MB");
  }
}
