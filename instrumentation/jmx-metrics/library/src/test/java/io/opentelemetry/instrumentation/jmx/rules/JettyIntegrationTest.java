/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class JettyIntegrationTest extends TargetSystemTest {

  // TODO: test with multiple versions

  private static final String DOCKER_IMAGE = "jetty:11";
  private static final int JETTY_PORT = 8080;

  @Test
  void testCollectedMetrics() {

    List<String> yamlFiles = Collections.singletonList("jetty.yaml");

    yamlFiles.forEach(this::validateYamlSyntax);

    List<String> jvmArgs = new ArrayList<>();
    jvmArgs.add(javaAgentJvmArgument());
    jvmArgs.addAll(javaPropertiesToJvmArgs(otelConfigProperties(yamlFiles)));

    GenericContainer<?> container =
        new GenericContainer<>(
                new ImageFromDockerfile()
                    .withDockerfileFromBuilder(
                        builder ->
                            builder
                                .from(DOCKER_IMAGE)
                                .run(
                                    "java",
                                    "-jar",
                                    "/usr/local/jetty/start.jar",
                                    "--add-to-startd=jmx,stats,http")
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
    return MetricsVerifier.create().add("jetty.dummy.metric", metric -> metric.hasDescription("hello"));
  }
}
