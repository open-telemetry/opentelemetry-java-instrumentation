/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Tests that the classloader-leak fix correctly closes agent SDK instruments when the application
 * classloader is collected.
 *
 * <p>The mechanism under test:
 *
 * <ol>
 *   <li>{@code CallbackAnchor.anchor()} anchors the bridging consumer in a static {@code List} on
 *       the helper class (tied to the app classloader's lifecycle) and wraps it in a {@code
 *       WeakRefConsumer} on the bootstrap classloader.
 *   <li>When the app classloader is collected, the static list is collected, removing the strong
 *       anchor. The {@code WeakReference} inside the {@code WeakRefConsumer} clears.
 *   <li>On the next SDK collection cycle, {@code WeakRefConsumer.accept()} detects the cleared
 *       reference and calls {@code close()} on the agent instrument.
 * </ol>
 *
 * <p>This test loads the OpenTelemetry API from a child-first classloader to simulate an
 * application classloader. The agent instruments the child classloader's copy of {@code
 * GlobalOpenTelemetry} and injects all bridge helpers (including {@code CallbackAnchor}) into it.
 * When the child classloader is released and collected, the anchored callbacks are collected too,
 * the weak references clear, and the instruments are closed.
 */
class CallbackGcCloseTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private String instrumentationName;
  private Meter meter;

  @BeforeEach
  void setupMeter(TestInfo test) {
    instrumentationName = "test-" + test.getDisplayName();
    meter = testing.getOpenTelemetry().getMeterProvider().meterBuilder(instrumentationName).build();
  }

  @Test
  void instrumentClosedWhenClassLoaderCollected() throws Exception {
    // 1. Build a child-first URLClassLoader that loads its own copy of the OTel API.
    //    The agent will instrument GlobalOpenTelemetry in this CL and inject all bridge helpers
    //    (including CallbackAnchor with its own static callbacks list).
    URL apiJar =
        io.opentelemetry.api.GlobalOpenTelemetry.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation();
    URL testClasses = GaugeRegistrar.class.getProtectionDomain().getCodeSource().getLocation();

    URLClassLoader childCl =
        new ChildFirstOtelClassLoader(new URL[] {apiJar, testClasses}, getClass().getClassLoader());

    // 2. Load GaugeRegistrar from the child CL and register a gauge.
    //    Because the child CL loads its own copy of the OTel API, the bridge creates an
    //    CallbackAnchor instance in the child CL with its own callbacks list.
    Class<?> registrar = childCl.loadClass(GaugeRegistrar.class.getName());
    registrar
        .getMethod("register", String.class, String.class)
        .invoke(null, instrumentationName, "test.gc.close");

    // 3. Verify the gauge is producing metrics.
    testing.waitAndAssertMetrics(
        instrumentationName,
        "test.gc.close",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42)))));

    // 4. Release the classloader — simulates app undeploy.
    WeakReference<ClassLoader> clRef = new WeakReference<>(childCl);
    childCl.close();
    childCl = null;
    registrar = null;

    // 5. Wait for GC to collect the classloader (and its CallbackAnchor.callbacks).
    GcUtils.awaitGc(clRef, Duration.ofSeconds(10));
    assertThat(clRef.get()).isNull();

    // 6. Force a metric collection so the SDK invokes WeakRefConsumer.accept(),
    //    which detects the cleared WeakReference and closes the instrument.
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    // 7. The gauge should no longer produce metrics.
    testing.waitAndAssertMetrics(
        instrumentationName, "test.gc.close", AbstractIterableAssert::isEmpty);
  }

  /**
   * Verifies that a gauge whose callback is still anchored (not GC'd) continues to produce metrics.
   * This is the control test for the above.
   */
  @Test
  void instrumentContinuesWhileCallbackAnchored() {
    Consumer<io.opentelemetry.api.metrics.ObservableLongMeasurement> callback =
        result -> result.record(99, Attributes.of(AttributeKey.stringKey("key"), "value"));

    meter.gaugeBuilder("test.gc.alive").ofLongs().buildWithCallback(callback);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test.gc.alive",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(99)))));

    // prevent callback from being collected during the test
    assertThat(callback).isNotNull();
  }

  /**
   * A URLClassLoader that loads {@code io.opentelemetry.api.*} and {@link GaugeRegistrar}
   * child-first. All other classes (including {@code io.opentelemetry.context.*}) delegate to the
   * parent as usual.
   */
  private static class ChildFirstOtelClassLoader extends URLClassLoader {

    ChildFirstOtelClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c != null) {
          return c;
        }
        if (name.startsWith("io.opentelemetry.api.")
            || name.equals(GaugeRegistrar.class.getName())) {
          try {
            c = findClass(name);
            if (resolve) {
              resolveClass(c);
            }
            return c;
          } catch (ClassNotFoundException e) {
            // fall through to parent
          }
        }
        return super.loadClass(name, resolve);
      }
    }
  }
}
