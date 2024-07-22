/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExperimentalFileDescriptorTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void registerObservers() {
    Supplier<Long> openFileDescriptor = () -> 10L;
    Supplier<Long> maxFileDescriptor = () -> 10000L;

    ExperimentalFileDescriptor.registerObservers(
        testing.getOpenTelemetry(), openFileDescriptor, maxFileDescriptor);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "os.file.descriptor.open",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("number of open file descriptors")
                        .hasUnit("{file}")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(10L)))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "os.file.descriptor.max",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("maximum number of file descriptors")
                        .hasUnit("{file}")
                        .hasDoubleGaugeSatisfying(
                            gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(10000L)))));
  }
}
