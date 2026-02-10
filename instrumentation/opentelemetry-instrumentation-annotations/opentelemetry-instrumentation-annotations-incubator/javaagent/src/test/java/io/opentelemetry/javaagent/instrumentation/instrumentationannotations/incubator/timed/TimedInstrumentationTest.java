/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed.TimedExample.METRIC_DESCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed.TimedExample.METRIC_NAME;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.incubator.timed.TimedExample.TO_STRING;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TimedInstrumentationTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  private static final String TIMED_INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-instrumentation-annotations-incubator";

  @Test
  void testExampleWithName() {
    new TimedExample().exampleWithName();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME, metric -> metric.hasName(METRIC_NAME).hasUnit("s"));
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
  void testExampleWithStaticAttributes() {
    new TimedExample().exampleWithStaticAttributes();
    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.with.static.attributes.duration")
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
  void testExampleWithAttributes() {
    new TimedExample().exampleWithAttributes("attr1", 2, new TimedExample.ToStringObject());
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
                                    Long.valueOf(2)
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.longKey("custom_attr1")))
                                        && TO_STRING.equals(
                                            p.getAttributes()
                                                .get(AttributeKey.stringKey("custom_attr2")))
                                        && "attr1"
                                            .equals(
                                                p.getAttributes()
                                                    .get(AttributeKey.stringKey("attribute1"))))));
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
                                                .get(AttributeKey.stringKey("error.type"))))));
  }

  @Test
  void testExampleWithReturnNameAttribute() {
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
                                    TimedExample.TO_STRING.equals(
                                        p.getAttributes()
                                            .get(AttributeKey.stringKey("returnValue"))))));
  }

  @Test
  void testCompletableFuture() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    new TimedExample().completableFuture(future);

    Thread.sleep(500); // sleep a bit just to make sure no metric is captured
    assertThat(testing.metrics()).isEmpty();

    future.complete("Done");

    testing.waitAndAssertMetrics(
        TIMED_INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("example.completable.future.duration")
                .satisfies(
                    metricData ->
                        assertThat(metricData.getData().getPoints())
                            .allMatch(
                                p ->
                                    "Done"
                                        .equals(
                                            p.getAttributes()
                                                .get(AttributeKey.stringKey("returnValue"))))));
  }
}
