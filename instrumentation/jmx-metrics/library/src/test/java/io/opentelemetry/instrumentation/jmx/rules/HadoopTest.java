/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attribute;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

class HadoopTest extends TargetSystemTest {

  public static final String ENDPOINT_PLACEHOLDER = "<<ENDPOINT_PLACEHOLDER>>";

  @Test
  void testMetrics_Hadoop2x() throws URISyntaxException, IOException {
    List<String> yamlFiles = Collections.singletonList("hadoop.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("bmedora/hadoop:2.9-base")
            .withCopyToContainer(
                Transferable.of(readAndPreprocessEnvFile("hadoop2-env.sh")),
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

  private String readAndPreprocessEnvFile(String fileName) throws URISyntaxException, IOException {
    Path path = Paths.get(getClass().getClassLoader().getResource(fileName).toURI());

    String data;
    try (Stream<String> lines = Files.lines(path)) {
      data =
          lines
              .map(line -> line.replace(ENDPOINT_PLACEHOLDER, getOtlpEndpoint()))
              .collect(Collectors.joining("\n"));
    }

    return data;
  }

  @Test
  void testMetrics_Hadoop3x() throws URISyntaxException, IOException {
    List<String> yamlFiles = Collections.singletonList("hadoop.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    // Hadoop startup script does not propagate env vars to launched hadoop daemons,
    // so all the env vars needs to be embedded inside the hadoop-env.sh file
    GenericContainer<?> target =
        new GenericContainer<>("loum/hadoop-pseudo:3.3.6")
            .withExposedPorts(9870, 9000)
            .withCopyToContainer(
                Transferable.of(readAndPreprocessEnvFile("hadoop3-env.sh")),
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
            "hadoop.dfs.data_node.live",
            metric ->
                metric
                    .hasDescription("Number of data nodes which are currently live.")
                    .hasUnit("{node}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute))
        .add(
            "hadoop.dfs.data_node.dead",
            metric ->
                metric
                    .hasDescription("Number of data nodes which are currently dead.")
                    .hasUnit("{node}")
                    .isUpDownCounter()
                    .hasDataPointsWithOneAttribute(nodeNameAttribute));
  }
}
