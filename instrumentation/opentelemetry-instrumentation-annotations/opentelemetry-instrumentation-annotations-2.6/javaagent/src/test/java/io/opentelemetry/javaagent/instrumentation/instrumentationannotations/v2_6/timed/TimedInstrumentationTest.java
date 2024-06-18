/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.timed;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.timed.TimedExample.METRIC_DESCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6.timed.TimedExample.METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TimedInstrumentationTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  private static final String TIMED_INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-2.6";

  @Test
  void testExampleWithAnotherName() {
    new TimedExample().exampleWithAnotherName();
    testing.waitAndAssertMetrics(TIMED_INSTRUMENTATION_NAME, metric -> metric.hasName(METRIC_NAME));
  }

  @Test
  void testExampleWithDescription() {
    new TimedExample().exampleWithDescription();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric.hasName("example.with.description.duration").hasDescription(METRIC_DESCRIPTION));
  }

  @Test
  void testExampleWithUnitSecondAnd2SecondLatency() throws InterruptedException {
    new TimedExample().exampleWithUnitSecondAnd2SecondLatency();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.unit.duration")
                .hasUnit("s")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getHistogramData().getPoints())
                            .allMatch(p -> p.getMax() < 5 && p.getMin() > 0)));
  }

  @Test
  void testExampleWithAdditionalAttributes1() {
    new TimedExample().exampleWithAdditionalAttributes1();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.attributes.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getData().getPoints())
                            .allMatch(
                                p ->
                                    "value1"
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.stringKey("key1")))
                                        && "value2"
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.stringKey("key2"))))));
  }

  @Test
  void testExampleWithAdditionalAttributes2() {
    new TimedExample().exampleWithAdditionalAttributes2();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.attributes2.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getData().getPoints())
                            .allMatch(
                                p ->
                                    "value1"
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.stringKey("key1")))
                                        && "value2"
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.stringKey("key2")))
                                        && null
                                            == p.getAttributes()
                                                .get(AttributeKey.stringKey("key3")))));
  }

  @Test
  void testExampleIgnore() throws Exception {
    new TimedExample().exampleIgnore();
    Thread.sleep(500);
    assertThat(testing.metrics()).isEmpty();
  }

  @Test
  void testExampleWithException() {
    try {
      new TimedExample().exampleWithException();
    } catch (IllegalStateException e) {
      // noop
    }
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.exception.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getData().getPoints())
                            .allMatch(
                                p ->
                                    IllegalStateException.class
                                        .getName()
                                        .equals(
                                            p.getAttributes()
                                                .get(AttributeKey.stringKey("exception"))))));
  }

  @Test
  void testExampleWithReturnValueAttribute() {
    new TimedExample().exampleWithReturnValueAttribute();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.return.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getData().getPoints())
                            .allMatch(
                                p ->
                                    TimedExample.RETURN_STRING.equals(
                                        p.getAttributes()
                                            .get(AttributeKey.stringKey("returnValue"))))));
  }
}
