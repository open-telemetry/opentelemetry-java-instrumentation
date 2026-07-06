package io.opentelemetry.instrumentation.jmx.rules;

import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

class WeaverContainer extends GenericContainer<WeaverContainer> {

  private static final Logger logger = LoggerFactory.getLogger(WeaverContainer.class);
  private static final int ADMIN_PORT = 4320;
  private static final int OTLP_PORT = 4317;

  WeaverContainer(Path registryRoot) {
    super("otel/weaver:v0.24.2");

    super.withExposedPorts(OTLP_PORT, ADMIN_PORT);
    super.waitingFor(Wait.forHttp("/health"));
    super.withCommand(
        "registry",
        "live-check",
        "--registry",
        "/registry",
        "--inactivity-timeout=0",
        "--output=http",
        "--format",
        "json");
    super.withLogConsumer(new Slf4jLogConsumer(logger));

    copyRegistry(registryRoot);
  }

  private void copyRegistry(Path root) {
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        @Override
        public @NonNull FileVisitResult visitFile(@NonNull Path file,
            @NonNull BasicFileAttributes attrs) {
          String containerPath = "/registry/" + root.relativize(file);
          withCopyFileToContainer(
              MountableFile.forHostPath(file),
              containerPath);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void stop() {
    String uri = "http://"+ this.getHost()+":" +this.getMappedPort(4320)+"/";
    WebClient client = WebClient.of(uri);
    try (HttpData result = client.post("/stop", new byte[0]).aggregate().join().content()) {
      // TODO: parse and store resulting JSON
      System.out.println(result.toString());
    }


    super.stop();
  }
}

