/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

class HadoopTest extends TargetSystemTest {

  private static final String ENDPOINT_PLACEHOLDER = "<<ENDPOINT_PLACEHOLDER>>";
  private static final String JMX_CONFIG_PLACEHOLDER = "<<JMX_CONFIG_PLACEHOLDER>>";

  @Test
  void testMetrics_Hadoop2x() throws IOException {
    Collection<String> yamlFiles = getAllRuleFilesForSystem("hadoop");

    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("bmedora/hadoop:2.9-base")
            .withCopyToContainer(
                Transferable.of(readAndPreprocessEnvFile("hadoop2-env.sh", yamlFiles)),
                "/hadoop/etc/hadoop/hadoop-env.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("test-host"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .withExposedPorts(50070, 50090)
            .waitingFor(Wait.forListeningPorts(50070, 50090));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startWeaverValidation(
        "hadoop.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("hadoop.")
                .checkRegisteredMetrics(
                    "hadoop.",
                    asList(
                        "hadoop.dfs.capacity.limit",
                        "hadoop.dfs.capacity.used",
                        "hadoop.dfs.block.count",
                        "hadoop.dfs.block.missing",
                        "hadoop.dfs.block.corrupt",
                        "hadoop.dfs.volume.failure.count",
                        "hadoop.dfs.file.count",
                        "hadoop.dfs.connection.count",
                        "hadoop.datanode.live",
                        "hadoop.datanode.dead"),
                    emptyList())
                .checkRegisteredAttributes("hadoop.", asList("hadoop.node.name"), emptyList()));

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private String readAndPreprocessEnvFile(String fileName, Collection<String> yamlFiles)
      throws IOException {
    try (InputStream input =
            requireNonNull(getClass().getClassLoader().getResourceAsStream(fileName));
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, UTF_8))) {

      String config = String.join(",", yamlFiles.stream().map(f -> "/" + f).collect(toList()));
      return reader
          .lines()
          .map(line -> line.replace(ENDPOINT_PLACEHOLDER, getOtlpEndpoint()))
          .map(line -> line.replace(JMX_CONFIG_PLACEHOLDER, config))
          .collect(joining("\n"));
    }
  }

  @Test
  void testMetrics_Hadoop3x() throws IOException {
    Collection<String> yamlFiles = getAllRuleFilesForSystem("hadoop");

    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("loum/hadoop-pseudo:3.3.6")
            .withExposedPorts(9870, 9000)
            .withCopyToContainer(
                Transferable.of(readAndPreprocessEnvFile("hadoop3-env.sh", yamlFiles)),
                "/opt/hadoop/etc/hadoop/hadoop-env.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("test-host"))
            .waitingFor(
                Wait.forListeningPorts(9870, 9000).withStartupTimeout(Duration.ofMinutes(3)));

    copyAgentToTarget(target);
    copyYamlFilesToTarget(target, yamlFiles);

    startWeaverValidation(
        "hadoop.yaml",
        result ->
            result
                .checkNothingUnregisteredWithPrefix("hadoop.")
                .checkRegisteredMetrics(
                    "hadoop.",
                    asList(
                        "hadoop.dfs.capacity.limit",
                        "hadoop.dfs.capacity.used",
                        "hadoop.dfs.block.count",
                        "hadoop.dfs.block.missing",
                        "hadoop.dfs.block.corrupt",
                        "hadoop.dfs.volume.failure.count",
                        "hadoop.dfs.file.count",
                        "hadoop.dfs.connection.count",
                        "hadoop.datanode.live",
                        "hadoop.datanode.dead"),
                    emptyList())
                .checkRegisteredAttributes("hadoop.", asList("hadoop.node.name"), emptyList()));

    startTarget(target);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {
    AttributeMatcher nodeNameAttribute = attribute("hadoop.node.name", "test-host");

    return MetricsVerifier.create()
        .disableStrictMode()
        .add(
            "hadoop.dfs.capacity.limit",
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
            "hadoop.datanode.live",
            metric ->
                metric
                    .hasDescription("Number of data nodes which are currently live.")
                    .hasUnit("{node}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.datanode.dead",
            metric ->
                metric
                    .hasDescription("Number of data nodes which are currently dead.")
                    .hasUnit("{node}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute));
  }
}
