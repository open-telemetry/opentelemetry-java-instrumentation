/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.enumeration;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.netmikey.logunit.api.LogCapturer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class HandlerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  LogCapturer logs =
      LogCapturer.create()
          .captureForLogger("io.opentelemetry.instrumentation.jmx.internal.engine.BeanFinder");

  @BeforeEach
  void reset() {
    BaseThreadHandler.createCount.set(0);
  }

  @Test
  void invalidHandler() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    // handler class won't be found since we don't set up the service class loader
    builder.addRules(getClass().getResourceAsStream("/jmx/rules/handler.yaml"));
    JmxTelemetry telemetry = builder.build();
    cleanup.deferCleanup(telemetry.start());

    logs.assertContains("Metric definition references unknown handler");
  }

  @Test
  void singleHandler(@TempDir Path tempDir) throws IOException {
    Path spiFile = tempDir.resolve(ExperimentalJmxMetricHandler.class.getName());
    Files.write(spiFile, ThreadHandler.class.getName().getBytes(UTF_8));

    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    builder.addRules(getClass().getResourceAsStream("/jmx/rules/handler.yaml"));
    builder.setServiceClassLoader(
        new ClassLoader(this.getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + ExperimentalJmxMetricHandler.class.getName())
                .equals(name)) {
              return enumeration(singletonList(spiFile.toUri().toURL()));
            }
            return super.getResources(name);
          }
        });
    JmxTelemetry telemetry = builder.build();
    cleanup.deferCleanup(telemetry.start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric ->
            metric
                .hasName("test.thread.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))));
    assertThat(BaseThreadHandler.createCount.get()).isEqualTo(1);
  }

  @Test
  void handlerList(@TempDir Path tempDir) throws IOException {
    Path spiFile = tempDir.resolve(ExperimentalJmxMetricHandler.class.getName());
    Files.write(
        spiFile,
        String.join("\n", asList(ThreadHandler.class.getName(), ThreadHandler2.class.getName()))
            .getBytes(UTF_8));

    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    builder.addRules(getClass().getResourceAsStream("/jmx/rules/handler-list.yaml"));
    builder.setServiceClassLoader(
        new ClassLoader(this.getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + ExperimentalJmxMetricHandler.class.getName())
                .equals(name)) {
              return enumeration(singletonList(spiFile.toUri().toURL()));
            }
            return super.getResources(name);
          }
        });
    JmxTelemetry telemetry = builder.build();
    cleanup.deferCleanup(telemetry.start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric ->
            metric
                .hasName("test.thread.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))),
        metric ->
            metric
                .hasName("test.thread.count2")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))));
    assertThat(BaseThreadHandler.createCount.get()).isEqualTo(2);
  }

  @Test
  void handlerMixed(@TempDir Path tempDir) throws IOException {
    Path spiFile = tempDir.resolve(ExperimentalJmxMetricHandler.class.getName());
    Files.write(spiFile, ThreadHandler.class.getName().getBytes(UTF_8));

    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    builder.addRules(getClass().getResourceAsStream("/jmx/rules/handler-mixed.yaml"));
    builder.setServiceClassLoader(
        new ClassLoader(this.getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + ExperimentalJmxMetricHandler.class.getName())
                .equals(name)) {
              return enumeration(singletonList(spiFile.toUri().toURL()));
            }
            return super.getResources(name);
          }
        });
    JmxTelemetry telemetry = builder.build();
    cleanup.deferCleanup(telemetry.start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric ->
            metric
                .hasName("test.thread.count")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))),
        metric ->
            metric
                .hasName("test.thread.count3")
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))));
    assertThat(BaseThreadHandler.createCount.get()).isEqualTo(1);
  }

  public static class ThreadHandler extends BaseThreadHandler {
    public ThreadHandler() {
      super("thread-handler", "test.thread.count");
    }
  }

  public static class ThreadHandler2 extends BaseThreadHandler {
    public ThreadHandler2() {
      super("thread-handler2", "test.thread.count2");
    }
  }

  public abstract static class BaseThreadHandler implements ExperimentalJmxMetricHandler {
    static final AtomicInteger createCount = new AtomicInteger();

    private final String name;
    private final String metricName;

    BaseThreadHandler(String name, String metricName) {
      this.name = name;
      this.metricName = metricName;
    }

    @Override
    public AutoCloseable create(Meter meter, Supplier<Detector> detectorSupplier) {
      createCount.incrementAndGet();
      return meter
          .upDownCounterBuilder(metricName)
          .buildWithCallback(
              measurement -> {
                Detector detector = detectorSupplier.get();
                if (detector != null) {
                  MBeanServerConnection connection = detector.getConnection();
                  for (ObjectName objectName : detector.getObjectNames()) {
                    try {
                      Object value = connection.getAttribute(objectName, "ThreadCount");
                      if (value instanceof Number) {
                        measurement.record(((Number) value).longValue(), Attributes.empty());
                      }
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  }
                }
              });
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
