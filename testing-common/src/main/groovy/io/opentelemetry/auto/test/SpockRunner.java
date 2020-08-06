/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.spockframework.runtime.Sputnik;

/**
 * Runs a spock test in an agent-friendly way.
 *
 * <ul>
 *   <li>Adds agent bootstrap classes to bootstrap classpath.
 * </ul>
 */
public class SpockRunner extends Sputnik {
  /**
   * An exact copy of {@link io.opentelemetry.auto.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}.
   *
   * <p>This list is needed to initialize the bootstrap classpath because Utils' static initializer
   * references bootstrap classes (e.g. AgentClassLoader).
   */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES_COPY = {
    "io.opentelemetry.auto.common.exec",
    "io.opentelemetry.auto.slf4j",
    "io.opentelemetry.auto.config",
    "io.opentelemetry.auto.bootstrap",
    "io.opentelemetry.auto.instrumentation.api",
    "io.opentelemetry.auto.shaded",
    "io.opentelemetry.auto.typedspan",
  };

  private static final String[] TEST_BOOTSTRAP_PREFIXES;

  static {
    ByteBuddyAgent.install();
    final String[] testBS = {
      "io.opentelemetry.OpenTelemetry", // OpenTelemetry API
      "io.opentelemetry.common", // OpenTelemetry API
      "io.opentelemetry.context", // OpenTelemetry API (context prop)
      "io.opentelemetry.correlationcontext", // OpenTelemetry API
      "io.opentelemetry.internal", // OpenTelemetry API
      "io.opentelemetry.metrics", // OpenTelemetry API
      "io.opentelemetry.trace", // OpenTelemetry API
      "io.grpc.Context", // OpenTelemetry API dependency
      "io.grpc.Deadline", // OpenTelemetry API dependency
      "io.grpc.PersistentHashArrayMappedTrie", // OpenTelemetry API dependency
      "io.grpc.ThreadLocalContextStorage", // OpenTelemetry API dependency
      "org.slf4j",
      "ch.qos.logback",
      // Tomcat's servlet classes must be on boostrap
      // when running tomcat test
      "javax.servlet.ServletContainerInitializer",
      "javax.servlet.ServletContext"
    };
    TEST_BOOTSTRAP_PREFIXES =
        Arrays.copyOf(
            BOOTSTRAP_PACKAGE_PREFIXES_COPY,
            BOOTSTRAP_PACKAGE_PREFIXES_COPY.length + testBS.length);
    for (int i = 0; i < testBS.length; ++i) {
      TEST_BOOTSTRAP_PREFIXES[i + BOOTSTRAP_PACKAGE_PREFIXES_COPY.length] = testBS[i];
    }
  }

  private final InstrumentationClassLoader customLoader;

  public SpockRunner(final Class<?> clazz)
      throws InitializationError, NoSuchFieldException, SecurityException, IllegalArgumentException,
          IllegalAccessException {
    super(shadowTestClass(clazz));
    assertNoBootstrapClassesInTestClass(clazz);
    // access the classloader created in shadowTestClass above
    final Field clazzField = Sputnik.class.getDeclaredField("clazz");
    try {
      clazzField.setAccessible(true);
      customLoader =
          (InstrumentationClassLoader) ((Class<?>) clazzField.get(this)).getClassLoader();
    } finally {
      clazzField.setAccessible(false);
    }
  }

  private static void assertNoBootstrapClassesInTestClass(final Class<?> testClass) {
    for (final Field field : testClass.getDeclaredFields()) {
      assertNotBootstrapClass(testClass, field.getType());
    }

    for (final Method method : testClass.getDeclaredMethods()) {
      assertNotBootstrapClass(testClass, method.getReturnType());
      for (final Class paramType : method.getParameterTypes()) {
        assertNotBootstrapClass(testClass, paramType);
      }
    }
  }

  private static void assertNotBootstrapClass(final Class<?> testClass, final Class<?> clazz) {
    if ((!clazz.isPrimitive()) && isBootstrapClass(clazz.getName())) {
      throw new IllegalStateException(
          testClass.getName()
              + ": Bootstrap classes are not allowed in test class field or method signatures. Offending class: "
              + clazz.getName());
    }
  }

  private static boolean isBootstrapClass(final String className) {
    for (int i = 0; i < TEST_BOOTSTRAP_PREFIXES.length; ++i) {
      if (className.startsWith(TEST_BOOTSTRAP_PREFIXES[i])) {
        return true;
      }
    }
    return false;
  }

  // Shadow the test class with bytes loaded by InstrumentationClassLoader
  private static Class<?> shadowTestClass(final Class<?> clazz) {
    try {
      final InstrumentationClassLoader customLoader =
          new InstrumentationClassLoader(
              io.opentelemetry.auto.test.SpockRunner.class.getClassLoader(), clazz.getName());
      return customLoader.shadow(clazz);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void run(final RunNotifier notifier) {
    final ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(customLoader);
      super.run(notifier);
    } finally {
      Thread.currentThread().setContextClassLoader(contextLoader);
    }
  }

  /** Run test classes in a classloader which loads test classes before delegating. */
  private static class InstrumentationClassLoader extends java.lang.ClassLoader {
    final ClassLoader parent;
    final String shadowPrefix;

    public InstrumentationClassLoader(final ClassLoader parent, final String shadowPrefix) {
      super(parent);
      this.parent = parent;
      this.shadowPrefix = shadowPrefix;
    }

    /** Forcefully inject the bytes of clazz into this classloader. */
    public Class<?> shadow(final Class<?> clazz) throws IOException {
      final Class<?> loaded = findLoadedClass(clazz.getName());
      if (loaded != null && loaded.getClassLoader() == this) {
        return loaded;
      }
      final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(clazz.getClassLoader());
      final byte[] classBytes = locator.locate(clazz.getName()).resolve();

      return defineClass(clazz.getName(), classBytes, 0, classBytes.length);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
        throws ClassNotFoundException {
      synchronized (super.getClassLoadingLock(name)) {
        final Class c = findLoadedClass(name);
        if (c != null) {
          return c;
        }
        if (name.startsWith(shadowPrefix)) {
          try {
            return shadow(super.loadClass(name, resolve));
          } catch (final Exception e) {
          }
        }

        return parent.loadClass(name);
      }
    }
  }
}
