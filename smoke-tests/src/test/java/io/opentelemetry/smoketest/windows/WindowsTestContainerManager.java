/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.windows;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import io.opentelemetry.smoketest.AbstractTestContainerManager;
import io.opentelemetry.smoketest.ResourceMapping;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.rnorth.ducttape.TimeoutException;
import org.rnorth.ducttape.ratelimits.RateLimiter;
import org.rnorth.ducttape.ratelimits.RateLimiterBuilder;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.OutputFrame;

public class WindowsTestContainerManager extends AbstractTestContainerManager {
  private static final Logger logger = LoggerFactory.getLogger(WindowsTestContainerManager.class);
  private static final Logger backendLogger = LoggerFactory.getLogger("Backend");

  private static final String NPIPE_URI = "npipe:////./pipe/docker_engine";

  private final DockerClient client =
      DockerClientImpl.getInstance(
          new DefaultDockerClientConfig.Builder().withDockerHost(NPIPE_URI).build(),
          new ApacheDockerHttpClient.Builder().dockerHost(URI.create(NPIPE_URI)).build());

  @Nullable private String natNetworkId = null;
  @Nullable private Container backend;
  @Nullable private Container target;

  @Override
  protected void startEnvironment() {
    natNetworkId =
        client
            .createNetworkCmd()
            .withDriver("nat")
            .withName(UUID.randomUUID().toString())
            .exec()
            .getId();

    String backendSuffix = "-windows-20210611.927888723";

    String backendImageName =
        "ghcr.io/open-telemetry/java-test-containers:smoke-fake-backend" + backendSuffix;
    if (!imageExists(backendImageName)) {
      pullImage(backendImageName);
    }

    backend =
        startContainer(
            backendImageName,
            command ->
                command
                    .withAliases(BACKEND_ALIAS)
                    .withExposedPorts(ExposedPort.tcp(BACKEND_PORT))
                    .withHostConfig(
                        HostConfig.newHostConfig()
                            .withAutoRemove(true)
                            .withNetworkMode(natNetworkId)
                            .withPortBindings(
                                new PortBinding(
                                    new Ports.Binding(null, null), ExposedPort.tcp(BACKEND_PORT)))),
            containerId -> {},
            new HttpWaiter(BACKEND_PORT, "/health", Duration.ofSeconds(60)),
            /* inspect= */ true,
            backendLogger);
  }

  @Override
  protected void stopEnvironment() {
    stopTarget();

    killContainer(backend);
    backend = null;

    if (natNetworkId != null) {
      client.removeNetworkCmd(natNetworkId);
      natNetworkId = null;
    }
  }

  @Override
  public int getBackendMappedPort() {
    return extractMappedPort(backend, BACKEND_PORT);
  }

  @Override
  public int getTargetMappedPort(int originalPort) {
    return extractMappedPort(target, originalPort);
  }

  @Override
  public Consumer<OutputFrame> startTarget(
      String targetImageName,
      String agentPath,
      String jvmArgsEnvVarName,
      Map<String, String> extraEnv,
      List<ResourceMapping> extraResources,
      TargetWaitStrategy waitStrategy) {
    stopTarget();

    if (!imageExists(targetImageName)) {
      pullImage(targetImageName);
    }

    List<String> environment = new ArrayList<>();
    getAgentEnvironment(jvmArgsEnvVarName)
        .forEach((key, value) -> environment.add(key + "=" + value));
    extraEnv.forEach((key, value) -> environment.add(key + "=" + value));

    target =
        startContainer(
            targetImageName,
            command ->
                command
                    .withExposedPorts(ExposedPort.tcp(TARGET_PORT))
                    .withHostConfig(
                        HostConfig.newHostConfig()
                            .withAutoRemove(true)
                            .withNetworkMode(natNetworkId)
                            .withPortBindings(
                                new PortBinding(
                                    new Ports.Binding(null, null), ExposedPort.tcp(TARGET_PORT))))
                    .withEnv(environment),
            containerId -> {
              try (InputStream agentFileStream = new FileInputStream(agentPath)) {
                copyFileToContainer(
                    containerId, IOUtils.toByteArray(agentFileStream), "/" + TARGET_AGENT_FILENAME);

                for (ResourceMapping resource : extraResources) {
                  copyResourceToContainer(
                      containerId, resource.resourcePath(), resource.containerPath());
                }
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            createTargetWaiter(waitStrategy),
            /* inspect= */ true,
            logger);
    return null;
  }

  @Override
  public void stopTarget() {
    killContainer(target);
    target = null;
  }

  private void pullImage(String imageName) {
    logger.info("Pulling {}", imageName);

    try {
      client.pullImageCmd(imageName).exec(new PullImageResultCallback()).awaitCompletion();
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private boolean imageExists(String imageName) {
    try {
      client.inspectImageCmd(imageName).exec();
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private void copyResourceToContainer(
      String containerId, String resourcePath, String containerPath) throws IOException {
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      copyFileToContainer(containerId, IOUtils.toByteArray(is), containerPath);
    }
  }

  private void copyFileToContainer(String containerId, byte[] content, String containerPath)
      throws IOException {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        TarArchiveOutputStream archiveStream = new TarArchiveOutputStream(output)) {
      archiveStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

      TarArchiveEntry entry = new TarArchiveEntry(containerPath);
      entry.setSize(content.length);
      entry.setMode(0100644);

      archiveStream.putArchiveEntry(entry);
      IOUtils.write(content, archiveStream);
      archiveStream.closeArchiveEntry();
      archiveStream.finish();

      client
          .copyArchiveToContainerCmd(containerId)
          .withTarInputStream(new ByteArrayInputStream(output.toByteArray()))
          .withRemotePath("/")
          .exec();
    }
  }

  private ContainerLogHandler consumeLogs(String containerId, Waiter waiter, Logger logger) {
    ContainerLogFrameConsumer consumer = new ContainerLogFrameConsumer();
    waiter.configureLogger(consumer);

    client
        .logContainerCmd(containerId)
        .withFollowStream(true)
        .withSince(0)
        .withStdOut(true)
        .withStdErr(true)
        .exec(consumer);

    consumer.addListener(new Slf4jDockerLogLineListener(logger));
    return consumer;
  }

  private static int extractMappedPort(Container container, int internalPort) {
    Ports.Binding[] binding =
        container
            .inspectResponse
            .getNetworkSettings()
            .getPorts()
            .getBindings()
            .get(ExposedPort.tcp(internalPort));
    if (binding != null && binding.length > 0 && binding[0] != null) {
      return Integer.parseInt(binding[0].getHostPortSpec());
    } else {
      throw new IllegalStateException("Port " + internalPort + " not mapped to host.");
    }
  }

  private Container startContainer(
      String imageName,
      Consumer<CreateContainerCmd> createAction,
      Consumer<String> prepareAction,
      Waiter waiter,
      boolean inspect,
      Logger logger) {

    if (waiter == null) {
      waiter = new NoOpWaiter();
    }

    CreateContainerCmd createCommand = client.createContainerCmd(imageName);
    createAction.accept(createCommand);

    String containerId = createCommand.exec().getId();

    prepareAction.accept(containerId);

    client.startContainerCmd(containerId).exec();
    ContainerLogHandler logHandler = consumeLogs(containerId, waiter, logger);

    InspectContainerResponse inspectResponse =
        inspect ? client.inspectContainerCmd(containerId).exec() : null;
    Container container = new Container(imageName, containerId, logHandler, inspectResponse);

    waiter.waitFor(container);
    return container;
  }

  private void killContainer(Container container) {
    if (container != null) {
      try {
        client.killContainerCmd(container.containerId).exec();
      } catch (NotFoundException e) {
        // The containers are flagged as remove-on-exit, so not finding them can be expected
      }
    }
  }

  private static class Container {
    public final String imageName;
    public final String containerId;
    public final ContainerLogHandler logConsumer;
    public final InspectContainerResponse inspectResponse;

    private Container(
        String imageName,
        String containerId,
        ContainerLogHandler logConsumer,
        InspectContainerResponse inspectResponse) {
      this.imageName = imageName;
      this.containerId = containerId;
      this.logConsumer = logConsumer;
      this.inspectResponse = inspectResponse;
    }
  }

  private static Waiter createTargetWaiter(TargetWaitStrategy strategy) {
    if (strategy instanceof TargetWaitStrategy.Log) {
      TargetWaitStrategy.Log details = (TargetWaitStrategy.Log) strategy;
      return new LogWaiter(Pattern.compile(details.regex), details.timeout);
    } else {
      return new PortWaiter(TARGET_PORT, Duration.ofSeconds(60));
    }
  }

  private interface Waiter {
    default void configureLogger(ContainerLogHandler logHandler) {}

    void waitFor(Container container);
  }

  private static class NoOpWaiter implements Waiter {
    @Override
    public void waitFor(Container container) {
      // No waiting
    }
  }

  private static class LogWaiter implements Waiter {
    private final Pattern regex;
    private final Duration timeout;
    private final CountDownLatch lineHit = new CountDownLatch(1);

    private LogWaiter(Pattern regex, Duration timeout) {
      this.regex = regex;
      this.timeout = timeout;
    }

    @Override
    public void configureLogger(ContainerLogHandler logHandler) {
      logHandler.addListener(
          (type, text) -> {
            if (lineHit.getCount() > 0) {
              if (regex.matcher(text).find()) {
                lineHit.countDown();
              }
            }
          });
    }

    @Override
    public void waitFor(Container container) {
      logger.info(
          "Waiting for container {}/{} to hit log line {}",
          container.imageName,
          container.containerId,
          regex.toString());

      try {
        lineHit.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }

      logger.info("Done waiting for container {}/{}", container.imageName, container.containerId);
    }
  }

  private static class HttpWaiter implements Waiter {
    private static final WebClient CLIENT =
        WebClient.builder().responseTimeout(Duration.ofSeconds(1)).build();

    private final int internalPort;
    private final String path;
    private final Duration timeout;
    private final RateLimiter rateLimiter =
        RateLimiterBuilder.newBuilder()
            .withRate(1, TimeUnit.SECONDS)
            .withConstantThroughput()
            .build();

    private HttpWaiter(int internalPort, String path, Duration timeout) {
      this.internalPort = internalPort;
      this.path = path;
      this.timeout = timeout;
    }

    @Override
    public void waitFor(Container container) {
      String url = "http://localhost:" + extractMappedPort(container, internalPort) + path;

      logger.info(
          "Waiting for container {}/{} on url {}", container.imageName, container.containerId, url);

      try {
        Unreliables.retryUntilSuccess(
            (int) timeout.toMillis(),
            TimeUnit.MILLISECONDS,
            () -> {
              rateLimiter.doWhenReady(
                  () -> {
                    AggregatedHttpResponse response = CLIENT.get(url).aggregate().join();

                    if (response.status().code() != 200) {
                      throw new IllegalStateException(
                          "Received status code " + response.status().code() + " from " + url);
                    }
                  });

              return true;
            });
      } catch (TimeoutException e) {
        throw new IllegalStateException(
            "Timed out waiting for container " + container.imageName, e);
      }

      logger.info("Done waiting for container {}/{}", container.imageName, container.containerId);
    }
  }

  private static class PortWaiter implements Waiter {
    private final int internalPort;
    private final Duration timeout;
    private final RateLimiter rateLimiter =
        RateLimiterBuilder.newBuilder()
            .withRate(1, TimeUnit.SECONDS)
            .withConstantThroughput()
            .build();

    private PortWaiter(int internalPort, Duration timeout) {
      this.internalPort = internalPort;
      this.timeout = timeout;
    }

    @Override
    public void waitFor(Container container) {
      logger.info(
          "Waiting for container {}/{} on port {}",
          container.imageName,
          container.containerId,
          internalPort);

      try {
        Unreliables.retryUntilSuccess(
            (int) timeout.toMillis(),
            TimeUnit.MILLISECONDS,
            () -> {
              rateLimiter.doWhenReady(
                  () -> {
                    int externalPort = extractMappedPort(container, internalPort);

                    try {
                      new Socket("localhost", externalPort).close();
                    } catch (IOException e) {
                      throw new IllegalStateException(
                          "Socket not listening yet: " + externalPort, e);
                    }
                  });

              return true;
            });
      } catch (TimeoutException e) {
        throw new IllegalStateException(
            "Timed out waiting for container " + container.imageName, e);
      }

      logger.info("Done waiting for container {}/{}", container.imageName, container.containerId);
    }
  }
}
