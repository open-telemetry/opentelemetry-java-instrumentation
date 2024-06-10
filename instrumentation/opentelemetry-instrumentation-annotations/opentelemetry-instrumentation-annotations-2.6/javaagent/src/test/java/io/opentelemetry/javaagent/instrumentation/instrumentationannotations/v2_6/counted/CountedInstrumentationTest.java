/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.counted;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.counted.CountedExample.ANOTHER_NAME_COUNT;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.counted.CountedExample.METRIC_DESCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.counted.CountedExample.METRIC_UNIT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CountedInstrumentationTest {

  @RegisterExtension
  public static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-2.6";
  private static final String COUNTED_DEFAULT_NAME = "method.invocation.count";

  @Test
  void testDefaultExample() {
    new CountedExample().defaultExample();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, metric -> metric.hasName(COUNTED_DEFAULT_NAME));
  }

  @Test
  void testExampleWithAnotherName() {
    new CountedExample().exampleWithAnotherName();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, metric -> metric.hasName(ANOTHER_NAME_COUNT));
  }

  @Test
  void testExampleWithDescriptionAndDefaultValue() {
    new CountedExample().exampleWithDescriptionAndDefaultValue();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, metric -> metric.hasName(COUNTED_DEFAULT_NAME).hasDescription(""));
  }

  @Test
  void testExampleWithUnitAndDefaultValue() {
    new CountedExample().exampleWithUnitAndDefaultValue();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, metric -> metric.hasName(COUNTED_DEFAULT_NAME).hasUnit(""));
  }

  @Test
  void testExampleWithDescription() {
    new CountedExample().exampleWithDescription();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric.hasName("example.with.description.count").hasDescription(METRIC_DESCRIPTION));
  }

  @Test
  void testExampleWithUnit() {
    new CountedExample().exampleWithUnit();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric -> metric.hasName("example.with.unit.count").hasUnit(METRIC_UNIT));
  }

  @Test
  void testExampleWithAdditionalAttributes1() {
    new CountedExample().exampleWithAdditionalAttributes1();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName(COUNTED_DEFAULT_NAME)
                .satisfies(
                    metricData -> {
                      assertThat(metricData.getData().getPoints())
                          .allMatch(
                              p ->
                                  "value1"
                                          .equals(
                                              p.getAttributes().get(AttributeKey.stringKey("key1")))
                                      && "value2"
                                          .equals(
                                              p.getAttributes()
                                                  .get(AttributeKey.stringKey("key2"))));
                    }));
  }

  @Test
  void testExampleWithAdditionalAttributes2() {
    new CountedExample().exampleWithAdditionalAttributes2();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName(COUNTED_DEFAULT_NAME)
                .satisfies(
                    metricData -> {
                      assertThat(metricData.getData().getPoints())
                          .allMatch(
                              p ->
                                  "value1"
                                          .equals(
                                              p.getAttributes().get(AttributeKey.stringKey("key1")))
                                      && "value2"
                                          .equals(
                                              p.getAttributes().get(AttributeKey.stringKey("key2")))
                                      && null
                                          == p.getAttributes().get(AttributeKey.stringKey("key3")));
                    }));
  }

  @Test
  void testExampleWithReturnAttribute() {
    new CountedExample().exampleWithReturnValueAttribute();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName(COUNTED_DEFAULT_NAME)
                .satisfies(
                    metricData -> {
                      assertThat(metricData.getData().getPoints())
                          .allMatch(
                              p ->
                                  CountedExample.RETURN_STRING.equals(
                                      p.getAttributes()
                                          .get(AttributeKey.stringKey("returnValue"))));
                    }));
  }

  @Test
  void testExampleWithException() {
    try {
      new CountedExample().exampleWithException();
    } catch (IllegalStateException e) {
      // noop
    }
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName(COUNTED_DEFAULT_NAME)
                .satisfies(
                    metricData -> {
                      assertThat(metricData.getData().getPoints())
                          .allMatch(
                              p ->
                                  IllegalStateException.class
                                      .getName()
                                      .equals(
                                          p.getAttributes()
                                              .get(AttributeKey.stringKey("exception"))));
                    }));
  }

  @Test
  void testExampleIgnore() throws Exception {
    new CountedExample().exampleIgnore();
    Thread.sleep(500); // sleep a bit just to make sure no metric is captured
    assertThat(testing.metrics()).isEmpty();
  }
}
