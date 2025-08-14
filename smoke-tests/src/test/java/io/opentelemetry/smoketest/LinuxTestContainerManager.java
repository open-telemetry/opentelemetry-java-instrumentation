/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
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
  private static final Logger backendLogger = LoggerFactory.getLogger("Backend");
  private static final Logger appLogger = LoggerFactory.getLogger("App");

  private final Network network = Network.newNetwork();
  private GenericContainer<?> backend = null;
  private GenericContainer<?> target = null;

  @Override
  protected void startEnvironment() {
    backend =
        new GenericContainer<>(
                DockerImageName.parse(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20250811.16876216352"))
            .withExposedPorts(BACKEND_PORT)
            .withEnv("JAVA_TOOL_OPTIONS", "-Xmx128m")
            .waitingFor(Wait.forHttp("/health").forPort(BACKEND_PORT))
            .withNetwork(network)
            .withNetworkAliases(BACKEND_ALIAS)
            .withLogConsumer(new Slf4jLogConsumer(backendLogger));
    backend.start();
  }

  @Override
  protected void stopEnvironment() {
    if (backend != null) {
      backend.stop();
      backend = null;
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
      boolean setServiceName,
      List<ResourceMapping> extraResources,
      List<Integer> extraPorts,
      TargetWaitStrategy waitStrategy,
      String[] command) {

    Consumer<OutputFrame> output = new ToStringConsumer();
    List<Integer> ports = new ArrayList<>();
    ports.add(TARGET_PORT);
    if (extraPorts != null) {
      ports.addAll(extraPorts);
    }
    target =
        new GenericContainer<>(DockerImageName.parse(targetImageName))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withExposedPorts(ports.toArray(new Integer[0]))
            .withNetwork(network)
            .withLogConsumer(output)
            .withLogConsumer(new Slf4jLogConsumer(appLogger))
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/" + TARGET_AGENT_FILENAME)
            .withEnv(getAgentEnvironment(jvmArgsEnvVarName, setServiceName))
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

    if (command != null) {
      target = target.withCommand(command);
    }

    try {
      target.start();
    } catch (ContainerLaunchException launchException) {
      // when container failed to start try to force a thread dump
      try {
        Container.ExecResult execResult = target.execInContainer("killall", "-3", "java");
        if (execResult.getExitCode() != 0) {
          logger.warn("Command execution failed {}", execResult);
        }
      } catch (IOException exception) {
        logger.warn("Command execution failed", exception);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }

      throw launchException;
    }

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
