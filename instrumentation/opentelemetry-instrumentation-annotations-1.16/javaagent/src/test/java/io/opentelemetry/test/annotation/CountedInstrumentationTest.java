/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CountedInstrumentationTest {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-1.16";

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void derivesDefaultName() {
    new CountedMethods().defaultName();

    assertCounter("CountedMethods.defaultName", 1);
  }

  @Test
  void incrementsForEachInvocation() {
    CountedMethods countedMethods = new CountedMethods();

    countedMethods.customName();
    countedMethods.customName();

    assertCounter("custom.count", 2);
  }

  @Test
  void incrementsWhenMethodThrows() {
    assertThatThrownBy(() -> new CountedMethods().throwsException())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    assertCounter("exception.count", 1);
  }

  @SuppressWarnings("UnicodeInCode")
  @Test
  void testUnicodeMethod() {
    CountedMethods countedMethods = new CountedMethods();
    countedMethods.ünicödeMethödNamë();
    assertCounter("CountedMethods._nic_deMeth_dNam_", 1);
  }

  @Test
  void doesNotDoubleCountBridgeMethod() {
    CountedMethods.GenericMethods<String> methods = new CountedMethods.StringCountedMethods();
    methods.customGeneric("foo");
    assertTotalCounterValue("custom.generic", 1);
  }

  private static void assertTotalCounterValue(String metricName, long expectedValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.satisfies(
                actualMetrics ->
                    assertThat(
                            actualMetrics.stream()
                                .flatMap(metric -> metric.getLongSumData().getPoints().stream())
                                .mapToLong(point -> point.getValue())
                                .sum())
                        .isEqualTo(expectedValue)));
  }

  private static void assertCounter(String metricName, long expectedValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName(metricName)
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(expectedValue)
                                        .hasAttributes(Attributes.empty()))));
  }
}
