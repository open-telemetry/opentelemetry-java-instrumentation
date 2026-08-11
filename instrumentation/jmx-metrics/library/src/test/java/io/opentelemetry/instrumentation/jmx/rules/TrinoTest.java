/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class TrinoTest extends TargetSystemTest {

  private static final int TRINO_PORT = 8080;

  @Test
  void testTrinoMetrics() throws Exception {
    List<String> yamlFiles = singletonList("trino.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>("trinodb/trino:483")
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(TRINO_PORT)
            .waitingFor(
                Wait.forHttp("/v1/info")
                    .forPort(TRINO_PORT)
                    .forResponsePredicate(
                        body ->
                            body.contains("\"starting\":false")
                                || body.contains("\"starting\": false")));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startWeaverValidation(
        "trino.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("trino.")
                .checkRegisteredMetrics(
                    "trino.",
                    asList(
                        "trino.memory.pool.free",
                        "trino.memory.query.killed.count",
                        "trino.query.running.count",
                        "trino.query.started.count",
                        "trino.query.failed.count",
                        "trino.query.failure.count",
                        "trino.query.execution.duration.p50",
                        "trino.query.input.rate.p90",
                        "trino.query.waiting_for_resources.count",
                        "trino.query.waiting_for_resources.duration.max",
                        "trino.task.input.data.size",
                        "trino.task.input.row.count"),
                    singletonList("trino.node.active.count"))
                .checkRegisteredAttributes(
                    "trino.",
                    asList("trino.memory.pool.name", "trino.query.failure.type"),
                    emptyList()));

    startTarget(target);

    ExecResult query =
        target.execInContainer("trino", "--execute", "SELECT count(*) FROM tpch.tiny.nation");
    assertThat(query.getExitCode()).isEqualTo(0);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    return MetricsVerifier.create()
        .disableStrictMode()
        .add(
            "trino.memory.pool.free",
            metric ->
                metric
                    .hasDescription(
                        "The amount of distributed memory currently free in the memory pool.")
                    .hasUnit("By")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(attributeWithAnyValue("trino.memory.pool.name")))
        .add(
            "trino.memory.query.killed.count",
            metric ->
                metric
                    .hasDescription("The number of queries killed due to running out of memory.")
                    .hasUnit("{query}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.running.count",
            metric ->
                metric
                    .hasDescription("The number of queries currently running.")
                    .hasUnit("{query}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.started.count",
            metric ->
                metric
                    .hasDescription("The number of queries started in the last five minutes.")
                    .hasUnit("{query}")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.failed.count",
            metric ->
                metric
                    .hasDescription("The number of failed queries in the last five minutes.")
                    .hasUnit("{query}")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.failure.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of failed queries in the last five minutes by failure type.")
                    .hasUnit("{query}")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("trino.query.failure.type", "internal")),
                        attributeGroup(attribute("trino.query.failure.type", "external")),
                        attributeGroup(attribute("trino.query.failure.type", "user_error"))))
        .add(
            "trino.query.execution.duration.p50",
            metric ->
                metric
                    .hasDescription(
                        "The 50th percentile query execution duration over the last five minutes.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.input.rate.p90",
            metric ->
                metric
                    .hasDescription(
                        "The 90th percentile wall-clock input data rate over the last five minutes.")
                    .hasUnit("By/s")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.waiting_for_resources.count",
            metric ->
                metric
                    .hasDescription("The number of queries currently waiting for resources.")
                    .hasUnit("{query}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.query.waiting_for_resources.duration.max",
            metric ->
                metric
                    .hasDescription("The longest time a query has been waiting for resources.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.task.input.data.size",
            metric ->
                metric
                    .hasDescription(
                        "The input data size processed by tasks in the last five minutes.")
                    .hasUnit("By")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "trino.task.input.row.count",
            metric ->
                metric
                    .hasDescription(
                        "The number of input rows processed by tasks in the last five minutes.")
                    .hasUnit("{row}")
                    .isGauge()
                    .hasDataPointsWithoutAttributes());
  }
}
