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

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void callbackDoesNotRetainApplicationClassLoader() throws Exception {
    String metricName = "test.micrometer.gc.close";
    String instrumentationName = "io.opentelemetry.micrometer-1.5";
    URL micrometerJar = Metrics.class.getProtectionDomain().getCodeSource().getLocation();
    URL testClasses = GaugeRegistrar.class.getProtectionDomain().getCodeSource().getLocation();
    URLClassLoader childLoader =
        new ChildFirstMicrometerClassLoader(
            new URL[] {micrometerJar, testClasses}, getClass().getClassLoader());

    Class<?> registrar = childLoader.loadClass(GaugeRegistrar.class.getName());
    Thread thread = Thread.currentThread();
    ClassLoader originalContextClassLoader = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(null);
      registrar.getMethod("register", String.class).invoke(null, metricName);
    } finally {
      thread.setContextClassLoader(originalContextClassLoader);
    }
    Class<?> callbackAnchor =
        Class.forName(MicrometerCallbackAnchor.class.getName(), false, childLoader);
    assertThat(callbackAnchor.getClassLoader()).isSameAs(childLoader);
    callbackAnchor = null;

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName(metricName)
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42))));

    WeakReference<ClassLoader> loaderReference = new WeakReference<>(childLoader);
    childLoader.close();
    childLoader = null;
    registrar = null;
    GcUtils.awaitGc(loaderReference, Duration.ofSeconds(10));
    assertThat(loaderReference.get()).isNull();

    testing.clearData();
    testing.waitAndAssertMetrics(instrumentationName, metricName, AbstractIterableAssert::isEmpty);
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
        if (name.startsWith("io.micrometer.")
            || name.equals(GaugeRegistrar.class.getName())
            || name.startsWith(GaugeRegistrar.class.getName() + "$")) {
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
