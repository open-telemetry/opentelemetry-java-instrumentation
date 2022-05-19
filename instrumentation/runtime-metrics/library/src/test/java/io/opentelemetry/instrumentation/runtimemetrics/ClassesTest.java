/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.ClassLoadingMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassesTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private ClassLoadingMXBean classBean;

  @Test
  void registerObservers() {
    when(classBean.getTotalLoadedClassCount()).thenReturn(3L);
    when(classBean.getUnloadedClassCount()).thenReturn(2L);
    when(classBean.getLoadedClassCount()).thenReturn(1);

    Classes.INSTANCE.registerObservers(testing.getOpenTelemetry(), classBean);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.classes.loaded",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Number of classes loaded since JVM start")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point.hasValue(3).hasAttributes(Attributes.empty())))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.classes.unloaded",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Number of classes unloaded since JVM start")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point.hasValue(2).hasAttributes(Attributes.empty())))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.classes.current_loaded",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasDescription("Number of classes currently loaded")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point.hasValue(1).hasAttributes(Attributes.empty())))));
  }
}
