/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17;

import static io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants.UNIT_CLASSES;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JfrClassesLoadedCountTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          builder -> builder.disableAllFeatures().enableFeature(JfrFeature.CLASS_LOAD_METRICS));

  @Test
  void shouldHaveJfrLoadedClassesCountEvents() throws Exception {
    Thread.sleep(2000);

    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.class.loaded")
                .hasDescription("Number of classes loaded since JVM start.")
                .hasUnit(UNIT_CLASSES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData ->
                                        assertThat(pointData.getValue())
                                            .isGreaterThanOrEqualTo(0)))),
        metric ->
            metric
                .hasName("jvm.class.count")
                .hasDescription("Number of classes currently loaded.")
                .hasUnit(UNIT_CLASSES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData ->
                                        assertThat(pointData.getValue())
                                            .isGreaterThanOrEqualTo(0)))),
        metric ->
            metric
                .hasName("jvm.class.unloaded")
                .hasDescription("Number of classes unloaded since JVM start.")
                .hasUnit(UNIT_CLASSES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData ->
                                        assertThat(pointData.getValue())
                                            .isGreaterThanOrEqualTo(0)))));
  }
}
