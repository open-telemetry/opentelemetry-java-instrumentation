/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class ClassLoadingTest {

  @Test
  void testDelegatesToBootstrapClassLoaderForAgentClasses() throws ClassNotFoundException {
    NonDelegatingUrlClassLoader classLoader = new NonDelegatingUrlClassLoader();
    Class<?> clazz =
        Class.forName(
            "io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge", false, classLoader);

    assertThat(clazz).isNotNull();
    assertThat(clazz.getClassLoader()).isNull();
  }

  @Test
  void testDelegatesToBootstrapClassLoaderForAgentClassesTwoArguments()
      throws ClassNotFoundException {
    NonDelegatingUrlClassLoader classLoader = new NonDelegatingUrlClassLoader();
    Class<?> clazz =
        classLoader.loadClass("io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge", false);
    assertThat(clazz).isNotNull();
    assertThat(clazz.getClassLoader()).isNull();
  }

  static class NonDelegatingUrlClassLoader extends URLClassLoader {

    NonDelegatingUrlClassLoader() {
      super(new URL[0]);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> clazz = findLoadedClass(name);
        if (clazz == null) {
          clazz = findClass(name);
        }
        if (resolve) {
          resolveClass(clazz);
        }
        return clazz;
      }
    }
  }
}
