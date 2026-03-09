/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

import com.sun.management.UnixOperatingSystemMXBean;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileDescriptorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private UnixOperatingSystemMXBean osBean;

  @BeforeEach
  void setUp() {
    // Skip tests on non-Unix systems since UnixOperatingSystemMXBean is only available on Unix
    OperatingSystemMXBean realOsBean = ManagementFactory.getOperatingSystemMXBean();
    assumeTrue(
        realOsBean instanceof UnixOperatingSystemMXBean,
        "Skipping test: UnixOperatingSystemMXBean is only available on Unix systems");
  }

  @Test
  void registerObservers() {
    // we have to test for positive and negative values in the same test as the metric is only
    // registered for positive values.
    when(osBean.getOpenFileDescriptorCount()).thenReturn(-1L, 42L);
    when(osBean.getMaxFileDescriptorCount()).thenReturn(-1L, 100L);
    FileDescriptor.registerObservers(testing.getOpenTelemetry().getMeter("test"), osBean);

    testing.waitAndAssertMetrics(
        "test",
        "jvm.file_descriptor.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Number of open file descriptors as reported by the JVM.")
                        .hasUnit("{file_descriptor}")
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(42)))));

    testing.waitAndAssertMetrics(
        "test",
        "jvm.file_descriptor.limit",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription(
                            "Measure of max open file descriptors as reported by the JVM.")
                        .hasUnit("{file_descriptor}")
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(100)))));
  }
}
