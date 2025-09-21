/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class IbmHttpsUrlConnectionTest {
  private static final Logger logger = LoggerFactory.getLogger(IbmHttpsUrlConnectionTest.class);

  private static final String TARGET_AGENT_FILENAME = "opentelemetry-javaagent.jar";
  private static final String agentPath =
      System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");
  private static final int BACKEND_PORT = 8080;
  private static final String BACKEND_ALIAS = "backend";

  private Network network;
  private GenericContainer<?> backend;
  private GenericContainer<?> target;
  private Container.ExecResult result;
  private TelemetryRetriever telemetryRetriever;

  @BeforeEach
  void setUp() {
    network = Network.newNetwork();
    backend =
        new GenericContainer<>(
                DockerImageName.parse(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20250811.16876216352"))
            .withExposedPorts(BACKEND_PORT)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx128m")
            .waitingFor(Wait.forHttp("/health").forPort(BACKEND_PORT))
            .withNetwork(network)
            .withNetworkAliases(BACKEND_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(logger));
    backend.start();
    telemetryRetriever =
        new TelemetryRetriever(backend.getMappedPort(BACKEND_PORT), Duration.ofSeconds(30));

    target =
        new GenericContainer<>(DockerImageName.parse("ibmjava:8-sdk"))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withNetwork(network)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(
                    "ibmhttpsurlconnection/IbmHttpsUrlConnectionTest.java"),
                "/IbmHttpsUrlConnectionTest.java")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("ibmhttpsurlconnection/start.sh", 777),
                "/start.sh")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("ibmhttpsurlconnection/test.sh", 777),
                "/test.sh")
            .waitingFor(Wait.forLogMessage(".*started.*\\n", 1))
            .withCommand("/bin/sh", "-c", "/start.sh");
    target.start();
  }

  @Test
  void testHttpsUrlConnection() throws Exception {
    result = target.execInContainer("/bin/sh", "-c", "/test.sh");
    assertThat(result.getExitCode()).isZero();

    assertThat(telemetryRetriever.waitForTraces())
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasNoParent()));
  }

  @AfterEach
  @SuppressWarnings("SystemOut")
  void tearDown() {
    if (result != null) {
      System.err.println(result);
    }
    if (target != null) {
      target.stop();
    }
    if (backend != null) {
      backend.stop();
    }
    if (network != null) {
      network.close();
    }
  }
}
