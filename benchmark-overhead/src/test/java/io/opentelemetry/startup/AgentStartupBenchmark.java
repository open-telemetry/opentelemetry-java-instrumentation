/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@EnabledIfSystemProperty(named = "startup.benchmark.enabled", matches = "true")
class AgentStartupBenchmark {
  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final long MEMORY_BYTES = 1024L * 1024 * 1024;
  private static final long NANO_CPUS = 2_000_000_000L;
  private static final String STARTUP_UPTIME_HEADER = "X-Startup-JVM-Uptime-Millis";
  private static final String JVM_VERSION_HEADER = "X-Startup-JVM-Version";
  private static final String APPLICATION_CLASS =
      "io/opentelemetry/smoketest/springboot/controller/WebController.class";

  @Test
  void measureStartup() throws Exception {
    Path agent = agent();
    Path recorder = recorder();
    Path fixture = fixtureClass();
    int samples = count("samples", 1);
    int warmups = count("warmups", 0);
    Path directory =
        Path.of(required("output"))
            .resolve(Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID());
    Files.createDirectories(directory);
    Properties metadata = new Properties();
    metadata.setProperty("status", "incomplete");
    metadata.setProperty("image.reference", StartupFixture.IMAGE);
    metadata.setProperty("samples", Integer.toString(samples));
    metadata.setProperty("warmups", Integer.toString(warmups));
    metadata.setProperty("resources", "quota=2 CPUs; memory=1 GiB; heap=512 MiB");
    metadata.setProperty(
        "exporters", "traces=none, metrics=none, logs=none; instrumentation enabled");
    try (JarFile jar = new JarFile(agent.toFile())) {
      metadata.setProperty(
          "agent.version",
          jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
    }
    metadata.setProperty("agent.sha256", checksum(agent));
    metadata.setProperty("recorder.sha256", checksum(recorder));
    metadata.setProperty("fixture.sha256", checksum(fixture));
    metadata.setProperty("agent.path", agent.toString());
    metadata.setProperty("recorder.path", recorder.toString());
    metadata.setProperty("source.revision", System.getProperty("startup.benchmark.revision"));
    metadata.setProperty("command.agent", "java " + String.join(" ", arguments(false)));
    metadata.setProperty("command.profile", "java " + String.join(" ", arguments(true)));
    saveMetadata(directory, metadata);

    DockerClient docker = DockerClientFactory.instance().client();
    List<AgentStartupReport.Sample> observations = new ArrayList<>();
    AgentStartupReport report = new AgentStartupReport(directory);
    for (int round = 0; round < warmups + samples; round++) {
      AgentStartupReport.Sample observation =
          sample(
              agent, recorder, fixture, round, round < warmups, false, directory, metadata, docker);
      observations.add(observation);
      report.append(observation);
      assertThat(observation.status()).isEqualTo("ok");
    }
    report.summarize(observations);
    sample(agent, recorder, fixture, -1, false, true, directory, metadata, docker);
    metadata.setProperty("status", "complete");
    saveMetadata(directory, metadata);
  }

  private static AgentStartupReport.Sample sample(
      Path agent,
      Path recorder,
      Path fixture,
      int round,
      boolean discarded,
      boolean profile,
      Path directory,
      Properties metadata,
      DockerClient docker)
      throws Exception {
    String name = profile ? "profile-agent" : "sample-" + round;
    double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    try (GenericContainer<?> application =
            container()
                .withCopyFileToContainer(MountableFile.forHostPath(agent), "/tmp/agent.jar")
                .withCopyFileToContainer(
                    MountableFile.forHostPath(recorder), "/tmp/startup-recorder.jar")
                .withCopyFileToContainer(
                    MountableFile.forHostPath(fixture), "/app/classes/" + APPLICATION_CLASS)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
                .withCommand(arguments(profile).toArray(String[]::new))
                .withExposedPorts(8080)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(TIMEOUT))
                .withStartupAttempts(1);
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
      try {
        application.start();
        HttpResponse<String> response = awaitReadiness(application, client);
        Instant cutoff = Instant.now();
        double startupSeconds =
            startupSeconds(
                response.statusCode(),
                response.body(),
                response.headers().firstValue(STARTUP_UPTIME_HEADER).orElse(null));
        String jvmVersion = response.headers().firstValue(JVM_VERSION_HEADER).orElse(null);
        if (jvmVersion == null || jvmVersion.isBlank()) {
          throw new IllegalArgumentException("Missing application JVM version response header");
        }

        var image = docker.inspectImageCmd(application.getContainerInfo().getImageId()).exec();
        metadata.setProperty("image.id", image.getId());
        metadata.setProperty("image.digests", String.valueOf(image.getRepoDigests()));
        metadata.setProperty("image.architecture", image.getArch());
        metadata.setProperty("image.os", image.getOs());
        metadata.setProperty("jvm.version", jvmVersion);
        saveMetadata(directory, metadata);
        String logs = application.getLogs();
        assertThat(logs).contains("opentelemetry-javaagent - version:");
        if (profile) {
          assertThat(logs.indexOf("Startup recording active before telemetry agent"))
              .isGreaterThanOrEqualTo(0)
              .isLessThan(logs.indexOf("opentelemetry-javaagent - version:"));
          var dump =
              application.execInContainer(
                  "jcmd", "1", "JFR.dump", "name=startup", "filename=/tmp/startup.jfr");
          assertThat(dump.getExitCode()).isZero();
          Path recording = directory.resolve("agent.jfr");
          application.copyFileFromContainer("/tmp/startup.jfr", recording.toString());
          StartupProfile.summarize(recording, cutoff, directory.resolve("agent-profile.txt"));
        }
        return new AgentStartupReport.Sample(round, discarded, "ok", startupSeconds, load);
      } catch (Throwable t) {
        if (!profile) {
          new AgentStartupReport(directory)
              .append(new AgentStartupReport.Sample(round, discarded, "failed", Double.NaN, load));
        }
        throw t;
      } finally {
        if (application.getContainerId() != null) {
          Files.writeString(directory.resolve(name + ".log"), application.getLogs(), UTF_8);
        }
      }
    }
  }

  private static HttpResponse<String> awaitReadiness(
      GenericContainer<?> application, HttpClient client) throws Exception {
    URI uri =
        URI.create(
            "http://"
                + application.getHost()
                + ":"
                + application.getMappedPort(8080)
                + "/greeting");
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    IOException lastConnectionFailure = null;
    while (System.nanoTime() < deadline) {
      try {
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        if (response.statusCode() == 200) {
          startupSeconds(
              response.statusCode(),
              response.body(),
              response.headers().firstValue(STARTUP_UPTIME_HEADER).orElse(null));
          return response;
        }
        if (response.statusCode() != 404 && response.statusCode() != 503) {
          throw new IOException("Readiness returned HTTP " + response.statusCode());
        }
      } catch (ConnectException e) {
        lastConnectionFailure = e;
      } catch (IOException e) {
        if (e.getMessage() != null && e.getMessage().startsWith("Readiness returned HTTP ")) {
          throw e;
        }
        lastConnectionFailure = e;
      }
      Thread.sleep(50);
    }
    throw new IOException(
        "Timed out waiting for successful /greeting readiness", lastConnectionFailure);
  }

  static double startupSeconds(int status, String body, String uptimeHeader) {
    if (status != 200 || !"Hi!".equals(body)) {
      throw new IllegalArgumentException(
          "Readiness must return HTTP 200 and body Hi!, got HTTP " + status + " and " + body);
    }
    if (uptimeHeader == null) {
      throw new IllegalArgumentException("Missing application JVM uptime response header");
    }
    long uptimeMillis;
    try {
      uptimeMillis = Long.parseLong(uptimeHeader);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid application JVM uptime: " + uptimeHeader, e);
    }
    if (uptimeMillis <= 0) {
      throw new IllegalArgumentException("Application JVM uptime must be positive");
    }
    return uptimeMillis / 1000.0;
  }

  static List<String> arguments(boolean profile) {
    List<String> arguments = new ArrayList<>(List.of("-Xmx512m"));
    if (profile) {
      arguments.add("-XX:FlightRecorderOptions=stackdepth=256");
      arguments.add("-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr");
    }
    arguments.add("-javaagent:/tmp/agent.jar");
    arguments.addAll(
        List.of("-cp", "/app/resources:/app/classes:/app/libs/*", StartupFixture.APPLICATION));
    return arguments;
  }

  private static GenericContainer<?> container() {
    return new GenericContainer<>(DockerImageName.parse(StartupFixture.IMAGE))
        .withEnv("JAVA_TOOL_OPTIONS", "")
        .withEnv("JDK_JAVA_OPTIONS", "")
        .withEnv("_JAVA_OPTIONS", "")
        .withEnv("OTEL_TRACES_EXPORTER", "none")
        .withEnv("OTEL_METRICS_EXPORTER", "none")
        .withEnv("OTEL_LOGS_EXPORTER", "none")
        .withCreateContainerCmdModifier(
            cmd ->
                cmd.getHostConfig()
                    .withMemory(MEMORY_BYTES)
                    .withMemorySwap(MEMORY_BYTES)
                    .withNanoCPUs(NANO_CPUS));
  }

  private static Path agent() {
    Path path = Path.of(required("agent")).toAbsolutePath();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Agent JAR does not exist: " + path);
    }
    return path;
  }

  private static Path fixtureClass() {
    Path path = Path.of(required("fixtureClass")).toAbsolutePath();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Startup fixture class does not exist: " + path);
    }
    return path;
  }

  private static Path recorder() {
    Path path = Path.of(required("recorder")).toAbsolutePath();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Startup recorder JAR does not exist: " + path);
    }
    return path;
  }

  private static String checksum(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static int count(String name, int minimum) {
    int value = Integer.parseInt(required(name));
    if (value < minimum) {
      throw new IllegalArgumentException(name + " must be at least " + minimum);
    }
    return value;
  }

  private static String required(String name) {
    String value = System.getProperty("startup.benchmark." + name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing startup.benchmark." + name);
    }
    return value;
  }

  private static void saveMetadata(Path directory, Properties metadata) throws IOException {
    try (OutputStream out = Files.newOutputStream(directory.resolve("metadata.properties"))) {
      metadata.store(out, "Ordinary Java agent startup benchmark");
    }
  }
}
