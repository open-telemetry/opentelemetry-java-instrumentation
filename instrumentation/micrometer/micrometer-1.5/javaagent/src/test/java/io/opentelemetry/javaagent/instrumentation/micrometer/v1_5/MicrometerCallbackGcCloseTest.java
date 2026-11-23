/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MicrometerCallbackGcCloseTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void callbackDoesNotRetainDelegatedMicrometerClassLoaders() throws Exception {
    String metricName = "test.micrometer.gc.close";
    LoaderReferences loaderReferences = registerGaugeInDisposableMicrometer(metricName);

    GcUtils.awaitGc(loaderReferences.pluginLoader, Duration.ofSeconds(10));
    GcUtils.awaitGc(loaderReferences.micrometerLoader, Duration.ofSeconds(10));
    assertThat(loaderReferences.pluginLoader.get()).isNull();
    assertThat(loaderReferences.micrometerLoader.get()).isNull();

    testing.clearData();
    testing.waitAndAssertMetrics(INSTRUMENTATION_NAME, metricName, AbstractIterableAssert::isEmpty);
  }

  @Test
  void callbackDoesNotRetainPluginWhenMicrometerStaysLoaded() throws Exception {
    String metricName = "test.micrometer.shared.gc.close";
    try (URLClassLoader micrometerLoader = newMicrometerClassLoader()) {
      WeakReference<ClassLoader> pluginLoader =
          registerGaugeAndAssert(metricName, micrometerLoader, false);

      GcUtils.awaitGc(pluginLoader, Duration.ofSeconds(10));

      testing.clearData();
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME, metricName, AbstractIterableAssert::isEmpty);
    }
  }

  private static LoaderReferences registerGaugeInDisposableMicrometer(String metricName)
      throws Exception {
    try (URLClassLoader micrometerLoader = newMicrometerClassLoader()) {
      return new LoaderReferences(
          new WeakReference<>(micrometerLoader),
          registerGaugeAndAssert(metricName, micrometerLoader, true));
    }
  }

  private static URLClassLoader newMicrometerClassLoader() {
    URL micrometerJar = Metrics.class.getProtectionDomain().getCodeSource().getLocation();
    return new ChildFirstMicrometerClassLoader(
        new URL[] {micrometerJar}, MicrometerCallbackGcCloseTest.class.getClassLoader());
  }

  private static WeakReference<ClassLoader> registerGaugeAndAssert(
      String metricName, ClassLoader micrometerLoader, boolean useGlobalRegistry) throws Exception {
    URL testClasses = GaugeRegistrar.class.getProtectionDomain().getCodeSource().getLocation();
    try (URLClassLoader pluginLoader =
        new ChildFirstGaugeClassLoader(new URL[] {testClasses}, micrometerLoader)) {
      Class<?> registrar = pluginLoader.loadClass(GaugeRegistrar.class.getName());
      assertThat(registrar.getClassLoader()).isSameAs(pluginLoader);
      assertThat(
              Class.forName("io.micrometer.core.instrument.Metrics", false, pluginLoader)
                  .getClassLoader())
          .isSameAs(micrometerLoader);

      Thread thread = Thread.currentThread();
      ClassLoader originalContextClassLoader = thread.getContextClassLoader();
      try {
        thread.setContextClassLoader(null);
        registrar
            .getMethod("register", String.class, boolean.class)
            .invoke(null, metricName, useGlobalRegistry);
      } finally {
        thread.setContextClassLoader(originalContextClassLoader);
      }

      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          metric ->
              metric
                  .hasName(metricName)
                  .hasDoubleGaugeSatisfying(
                      gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42))));

      return new WeakReference<>(pluginLoader);
    }
  }

  private static final class LoaderReferences {

    private final WeakReference<ClassLoader> micrometerLoader;
    private final WeakReference<ClassLoader> pluginLoader;

    private LoaderReferences(
        WeakReference<ClassLoader> micrometerLoader, WeakReference<ClassLoader> pluginLoader) {
      this.micrometerLoader = micrometerLoader;
      this.pluginLoader = pluginLoader;
    }
  }

  private static class ChildFirstMicrometerClassLoader extends URLClassLoader {

    ChildFirstMicrometerClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        if (name.startsWith("io.micrometer.")) {
          try {
            Class<?> type = findClass(name);
            if (resolve) {
              resolveClass(type);
            }
            return type;
          } catch (ClassNotFoundException ignored) {
            // fall through to parent
          }
        }
        return super.loadClass(name, resolve);
      }
    }
  }

  private static class ChildFirstGaugeClassLoader extends URLClassLoader {

    ChildFirstGaugeClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
          return loaded;
        }
        if (name.equals(GaugeRegistrar.class.getName())) {
          try {
            Class<?> type = findClass(name);
            if (resolve) {
              resolveClass(type);
            }
            return type;
          } catch (ClassNotFoundException ignored) {
            // fall through to parent
          }
        }
        return super.loadClass(name, resolve);
      }
    }
  }
}
