/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class LinuxTestContainerManager extends AbstractTestContainerManager {
  private static final Logger logger = LoggerFactory.getLogger(LinuxTestContainerManager.class);
  private static final Logger collectorLogger = LoggerFactory.getLogger("Collector");
  private static final Logger backendLogger = LoggerFactory.getLogger("Backend");

  private final Network network = Network.newNetwork();
  private GenericContainer<?> backend = null;
  private GenericContainer<?> collector = null;
  private GenericContainer<?> target = null;

  @Override
  protected void startEnvironment() {
    backend =
        new GenericContainer<>(
                DockerImageName.parse(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20210918.1248928123"))
            .withExposedPorts(BACKEND_PORT)
            .waitingFor(Wait.forHttp("/health").forPort(BACKEND_PORT))
            .withNetwork(network)
            .withNetworkAliases(BACKEND_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(backendLogger));
    backend.start();

    collector =
        new GenericContainer<>(DockerImageName.parse("otel/opentelemetry-collector-contrib:latest"))
            .dependsOn(backend)
            .withNetwork(network)
            .withNetworkAliases(COLLECTOR_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(collectorLogger))
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(COLLECTOR_CONFIG_RESOURCE), "/etc/otel.yaml")
            .withCommand("--config /etc/otel.yaml");
    collector.start();
  }

  @Override
  protected void stopEnvironment() {
    if (backend != null) {
      backend.stop();
      backend = null;
    }

    if (collector != null) {
      collector.stop();
      collector = null;
    }

    network.close();
  }

  @Override
  public int getBackendMappedPort() {
    return backend.getMappedPort(BACKEND_PORT);
  }

  @Override
  public int getTargetMappedPort(int originalPort) {
    return target.getMappedPort(originalPort);
  }

  @Override
  public Consumer<OutputFrame> startTarget(
      String targetImageName,
      String agentPath,
      String jvmArgsEnvVarName,
      Map<String, String> extraEnv,
      List<ResourceMapping> extraResources,
      TargetWaitStrategy waitStrategy) {

    Consumer<OutputFrame> output = new ToStringConsumer();
    target =
        new GenericContainer<>(DockerImageName.parse(targetImageName))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withExposedPorts(TARGET_PORT)
            .withNetwork(network)
            .withLogConsumer(output)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
            .withEnv(getAgentEnvironment(jvmArgsEnvVarName))
            .withEnv(extraEnv);

    for (ResourceMapping resource : extraResources) {
      target.withCopyFileToContainer(
          MountableFile.forClasspathResource(resource.resourcePath()), resource.containerPath());
    }

    if (waitStrategy != null) {
      if (waitStrategy instanceof TargetWaitStrategy.Log) {
        target =
            target.waitingFor(
                Wait.forLogMessage(((TargetWaitStrategy.Log) waitStrategy).regex, 1)
                    .withStartupTimeout(waitStrategy.timeout));
      }
    }
    target.start();
    return output;
  }

  @Override
  public void stopTarget() {
    if (target != null) {
      target.stop();
      target = null;
    }
  }
}
