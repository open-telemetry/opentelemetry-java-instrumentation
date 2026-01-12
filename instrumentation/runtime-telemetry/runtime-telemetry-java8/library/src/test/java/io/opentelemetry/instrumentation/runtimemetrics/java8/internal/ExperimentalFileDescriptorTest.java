/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
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
class ExperimentalFileDescriptorTest {

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
  // verify that mock is called with the correct value
  void registerObservers() {
    when(osBean.getOpenFileDescriptorCount()).thenReturn(42L);
    ExperimentalFileDescriptor.registerObservers(testing.getOpenTelemetry(), osBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.file_descriptor.count",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Number of open file descriptors as reported by the JVM.")
                        .hasUnit("{file_descriptor}")
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(42)))));
  }

  @Test
  // Verify that no metrics are emitted with non-zero values
  void registerObservers_NegativeValue() {
    when(osBean.getOpenFileDescriptorCount()).thenReturn(-1L);
    ExperimentalFileDescriptor.registerObservers(testing.getOpenTelemetry(), osBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.file_descriptor.count",
        metrics ->
            metrics.allSatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Number of open file descriptors as reported by the JVM.")
                        .hasUnit("{file_descriptor}")
                        .hasLongSumSatisfying(
                            sum -> sum.hasPointsSatisfying(point -> point.hasValue(0)))));
  }
}
