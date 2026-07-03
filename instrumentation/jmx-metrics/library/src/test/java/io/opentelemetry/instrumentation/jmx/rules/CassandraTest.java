/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcherGroup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

class CassandraTest extends TargetSystemTest {

  private static final int CASSANDRA_PORT = 9042;

  @Test
  void testCassandraMetrics() {
    List<String> yamlFiles = singletonList("experimental-cassandra.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> target =
        new GenericContainer<>("cassandra:5.0.2")
            .withEnv("JAVA_TOOL_OPTIONS", String.join(" ", jvmArgs))
            // speed up single-node startup from ~1min to ~15s
            .withEnv(
                "JVM_EXTRA_OPTS",
                "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=0")
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(CASSANDRA_PORT)
            .waitingFor(Wait.forListeningPorts(CASSANDRA_PORT));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    return MetricsVerifier.create()
        .add(
            "cassandra.compaction.tasks.completed",
            metric ->
                metric
                    .hasDescription("Number of completed compactions since server start.")
                    .hasUnit("{task}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.compaction.tasks.pending",
            metric ->
                metric
                    .hasDescription("Estimated number of compactions remaining to perform.")
                    .hasUnit("{task}")
                    .isGauge()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.load",
            metric ->
                metric
                    .hasDescription("Size of the on disk data size this node manages.")
                    .hasUnit("By")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.hints.count",
            metric ->
                metric
                    .hasDescription(
                        "Number of hint messages written to this node since server start.")
                    .hasUnit("{hint}")
                    .isCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.storage.hints.in_progress",
            metric ->
                metric
                    .hasDescription("Number of hints attempting to be sent currently.")
                    .hasUnit("{hint}")
                    .isUpDownCounter()
                    .hasDataPointsWithoutAttributes())
        .add(
            "cassandra.client.request.latency.p50",
            metric ->
                metric
                    .hasDescription("Request latency 50th percentile by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.latency.p99",
            metric ->
                metric
                    .hasDescription("Request latency 99th percentile by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.latency.max",
            metric ->
                metric
                    .hasDescription("Maximum request latency by operation.")
                    .hasUnit("s")
                    .isGauge()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write")),
                        attributeGroup(attribute("cassandra.operation", "rangeslice"))))
        .add(
            "cassandra.client.request.count",
            metric ->
                metric
                    .hasDescription("Number of requests by operation.")
                    .hasUnit("{request}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("cassandra.operation", "rangeslice")),
                        attributeGroup(attribute("cassandra.operation", "read")),
                        attributeGroup(attribute("cassandra.operation", "write"))))
        .add(
            "cassandra.client.request.error",
            metric ->
                metric
                    .hasDescription("Number of request errors by operation.")
                    .hasUnit("{error}")
                    .isCounter()
                    .hasDataPointsWithAttributes(
                        errorAttributesGroup("rangeslice", "timeout"),
                        errorAttributesGroup("rangeslice", "failure"),
                        errorAttributesGroup("rangeslice", "unavailable"),
                        errorAttributesGroup("read", "timeout"),
                        errorAttributesGroup("read", "failure"),
                        errorAttributesGroup("read", "unavailable"),
                        errorAttributesGroup("write", "timeout"),
                        errorAttributesGroup("write", "failure"),
                        errorAttributesGroup("write", "unavailable")));
  }

  private static AttributeMatcherGroup errorAttributesGroup(String operation, String status) {
    return attributeGroup(
        attribute("cassandra.operation", operation), attribute("cassandra.status", status));
  }
}
