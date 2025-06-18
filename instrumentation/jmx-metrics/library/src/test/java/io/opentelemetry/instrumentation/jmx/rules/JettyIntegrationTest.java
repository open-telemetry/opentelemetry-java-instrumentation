/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeGroup;
import static io.opentelemetry.instrumentation.jmx.rules.assertions.DataPointAttributes.attributeWithAnyValue;

import io.opentelemetry.instrumentation.jmx.rules.assertions.AttributeMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class JettyIntegrationTest extends TargetSystemTest {

  private static final int JETTY_PORT = 8080;

  @ParameterizedTest
  @ValueSource(ints = {11}) // TODO: add support for Jetty 12
  void testCollectedMetrics(int jettyMajorVersion) {

    List<String> yamlFiles = Collections.singletonList("jetty.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    String jettyModules = "jmx,http,";
    if (jettyMajorVersion >= 12) {
      jettyModules += "statistics";
    } else {
      jettyModules += "stats";
    }
    String addModulesArg = "--add-to-startd=" + jettyModules;

    GenericContainer<?> container =
        new GenericContainer<>(
                new ImageFromDockerfile()
                    .withDockerfileFromBuilder(
                        builder ->
                            builder
                                .from("jetty:" + jettyMajorVersion)
                                .run(
                                    "java",
                                    "-jar",
                                    "/usr/local/jetty/start.jar",
                                    addModulesArg)
                                .run("mkdir -p /var/lib/jetty/webapps/ROOT/")
                                .run("touch /var/lib/jetty/webapps/ROOT/index.html")
                                .build()))
            .withEnv("JAVA_OPTIONS", String.join(" ", jvmArgs))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withExposedPorts(JETTY_PORT)
            .waitingFor(Wait.forListeningPorts(JETTY_PORT));

    copyFilesToTarget(container, yamlFiles);

    startTarget(container);

    verifyMetrics(createMetricsVerifier());
  }

  private static MetricsVerifier createMetricsVerifier() {

    AttributeMatcher threadPoolId = attributeWithAnyValue("jetty.thread.pool.id");

    return MetricsVerifier.create()
        .add(
            "jetty.thread.count",
            metric ->
                metric
                    .hasDescription("The current number of threads")
                    .hasUnit("{thread}")
                    .hasDataPointsWithOneAttribute(threadPoolId)
                    .isUpDownCounter())
        .add(
            "jetty.thread.limit",
            metric ->
                metric
                    .hasDescription("The configured maximum number of threads in the pool")
                    .hasUnit("{thread}")
                    .hasDataPointsWithOneAttribute(threadPoolId)
                    .isUpDownCounter())
        .add(
            "jetty.thread.idle.count",
            metric ->
                metric
                    .hasDescription("The current number of idle threads")
                    .hasUnit("{thread}")
                    .hasDataPointsWithOneAttribute(threadPoolId)
                    .isUpDownCounter())
        .add(
            "jetty.thread.busy.count",
            metric ->
                metric
                    .hasDescription("The current number of busy threads")
                    .hasUnit("{thread}")
                    .hasDataPointsWithOneAttribute(threadPoolId)
                    .isUpDownCounter())
        .add(
            "jetty.thread.queue.size",
            metric ->
                metric
                    .hasDescription("The current job queue size")
                    .hasUnit("{thread}")
                    .hasDataPointsWithOneAttribute(threadPoolId)
                    .isUpDownCounter())
        .add(
            "jetty.select.count",
            metric ->
                metric
                    .hasDescription("The number of select calls")
                    .hasUnit("{operation}")
                    .hasDataPointsWithAttributes(
                        attributeGroup(
                            attributeWithAnyValue("jetty.selector.id"),
                            attributeWithAnyValue("jetty.selector.context")))
                    .isCounter());
  }
}
