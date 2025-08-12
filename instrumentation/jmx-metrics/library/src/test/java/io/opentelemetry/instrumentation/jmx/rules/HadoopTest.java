/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

class HadoopTest extends TargetSystemTest {
  @Test
  void testMetrics_Hadoop2x() {
    List<String> yamlFiles = Collections.singletonList("hadoop.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);


    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("bmedora/hadoop:2.9-base")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("hadoop2-env.sh", 0400),
                "/hadoop/etc/hadoop/hadoop-env.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("test-host"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(50070)
            .waitingFor(Wait.forListeningPorts(50070));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  @Test
  void testMetrics_Hadoop3x() {
    List<String> yamlFiles = Collections.singletonList("hadoop.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("loum/hadoop-pseudo:3.3.6")
            .withExposedPorts(9870, 9000)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("hadoop3-env.sh", 0644),
                "/opt/hadoop/etc/hadoop/hadoop-env.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("test-host"))
            .waitingFor(
                Wait.forListeningPorts(9870, 9000).withStartupTimeout(Duration.ofMinutes(3)));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher nodeNameAttribute = attribute("hadoop.node.name", "test-host");

    return MetricsVerifier.create()
        .disableStrictMode()
        .add(
            "hadoop.dfs.capacity",
            metric ->
                metric
                    .hasDescription("Current raw capacity of DataNodes.")
                    .hasUnit("By")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.capacity.used",
            metric ->
                metric
                    .hasDescription("Current used capacity across all DataNodes.")
                    .hasUnit("By")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.block.count",
            metric ->
                metric
                    .hasDescription("Current number of allocated blocks in the system.")
                    .hasUnit("{block}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.block.missing",
            metric ->
                metric
                    .hasDescription("Current number of missing blocks.")
                    .hasUnit("{block}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.block.corrupt",
            metric ->
                metric
                    .hasDescription("Current number of blocks with corrupt replicas.")
                    .hasUnit("{block}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.volume.failure.count",
            metric ->
                metric
                    .hasDescription("Total number of volume failures across all DataNodes.")
                    .hasUnit("{failure}")
                    .isCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.file.count",
            metric ->
                metric
                    .hasDescription("Current number of files and directories.")
                    .hasUnit("{file}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.connection.count",
            metric ->
                metric
                    .hasDescription("Current number of connections.")
                    .hasUnit("{connection}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.data_node.count",
            metric ->
                metric
                    .hasDescription("The number of DataNodes.")
                    .hasUnit("{node}")
                    .isUpDownCounter()
                    .hasDataPointsWithAttributes(
                        attributeGroup(attribute("hadoop.node.state", "live"), nodeNameAttribute),
                        attributeGroup(attribute("hadoop.node.state", "dead"), nodeNameAttribute)));
  }
}
