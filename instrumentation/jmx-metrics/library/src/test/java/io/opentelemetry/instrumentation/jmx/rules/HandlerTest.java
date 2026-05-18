/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.enumeration;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.jmx.JmxMetricHandler;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.function.Supplier;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class HandlerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void testHandler(@TempDir Path tempDir) throws Exception {
    Path spiFile = tempDir.resolve(JmxMetricHandler.class.getName());
    Files.write(spiFile, ThreadHandler.class.getName().getBytes(UTF_8));

    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    builder.addRules(getClass().getResourceAsStream("/jmx/rules/handler.yaml"));
    builder.setServiceClassLoader(
        new ClassLoader(this.getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            if (("META-INF/services/" + JmxMetricHandler.class.getName()).equals(name)) {
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
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point.hasValueSatisfying(value -> value.isGreaterThan(0)))));
  }

  public static class ThreadHandler implements JmxMetricHandler {

    @Override
    public AutoCloseable create(Meter meter, Supplier<Detector> detectorSupplier) {
      return meter
          .counterBuilder("test.thread.count")
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
      return "thread-handler";
    }
  }
}
