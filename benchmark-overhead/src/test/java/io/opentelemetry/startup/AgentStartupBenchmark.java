/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
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
import org.testcontainers.utility.MountableFile;

@EnabledIfSystemProperty(named = "startup.benchmark.enabled", matches = "true")
class AgentStartupBenchmark {
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  @Test
  void measureStartup() throws Exception {
    Path agent = agent();
    int samples = count("samples", 1);
    int warmups = count("warmups", 0);
    Path directory =
        Path.of(required("output"))
            .resolve(Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID());
    Files.createDirectories(directory);
    Properties metadata = new Properties();
    metadata.setProperty("status", "incomplete");
    metadata.setProperty("image.reference", AotStartupBenchmark.IMAGE);
    metadata.setProperty("samples", Integer.toString(samples));
    metadata.setProperty("warmups", Integer.toString(warmups));
    metadata.setProperty("resources", "cpuset=0,1; quota=2 CPUs; memory=1 GiB; heap=512 MiB");
    metadata.setProperty(
        "exporters", "traces=none, metrics=none, logs=none; instrumentation enabled");
    try (JarFile jar = new JarFile(agent.toFile())) {
      metadata.setProperty(
          "agent.version",
          jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(agent)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    }
    metadata.setProperty("agent.sha256", HexFormat.of().formatHex(digest.digest()));
    metadata.setProperty("agent.path", agent.toString());
    metadata.setProperty("source.revision", System.getProperty("startup.benchmark.sourceRevision"));
    metadata.setProperty("command.agent", "java " + String.join(" ", arguments(false)));
    metadata.setProperty("command.profile", "java " + String.join(" ", arguments(true)));
    saveMetadata(directory, metadata);
    DockerClient docker = DockerClientFactory.instance().client();
    String volume = AgentStartupVolume.create(docker);
    List<AgentStartupReport.Sample> observations = new ArrayList<>();
    AgentStartupReport report = new AgentStartupReport(directory);
    try {
      String image = prepare(docker, volume, agent, directory, metadata);
      saveMetadata(directory, metadata);
      for (int round = 0; round < warmups + samples; round++) {
        AgentStartupReport.Sample observation =
            sample(image, volume, round, round < warmups, false, directory);
        observations.add(observation);
        report.append(observation);
        assertThat(observation.status()).isEqualTo("ok");
      }
      report.summarize(observations);
      sample(image, volume, -1, false, true, directory);
      metadata.setProperty("status", "complete");
      saveMetadata(directory, metadata);
    } finally {
      docker.removeVolumeCmd(volume).exec();
    }
  }

  private static String prepare(
      DockerClient docker, String volume, Path agent, Path directory, Properties metadata)
      throws Exception {
    try (GenericContainer<?> preparation =
        container(AotStartupBenchmark.IMAGE, volume)
            .withCopyFileToContainer(MountableFile.forHostPath(agent), "/benchmark/agent.jar")
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of(required("recorder"))),
                "/benchmark/startup-recorder.jar")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
            .withCommand(
                "-c",
                "set -eu; test -d /app/classes; test -d /app/resources; test -d /app/libs; "
                    + "java -version >/benchmark/java-version.log 2>&1; "
                    + "echo BENCHMARK_READY; exec sleep infinity")
            .waitingFor(Wait.forLogMessage(".*BENCHMARK_READY.*", 1).withStartupTimeout(TIMEOUT))) {
      try {
        preparation.start();
        var image = docker.inspectImageCmd(preparation.getContainerInfo().getImageId()).exec();
        metadata.setProperty("image.id", image.getId());
        metadata.setProperty("image.digests", String.valueOf(image.getRepoDigests()));
        metadata.setProperty("image.architecture", image.getArch());
        preparation.copyFileFromContainer(
            "/benchmark/java-version.log", directory.resolve("java-version.log").toString());
        metadata.setProperty(
            "jvm.version", Files.readString(directory.resolve("java-version.log"), UTF_8).strip());
        return image.getId();
      } finally {
        if (preparation.getContainerId() != null) {
          Files.writeString(directory.resolve("preparation.log"), preparation.getLogs(), UTF_8);
        }
      }
    }
  }

  private static AgentStartupReport.Sample sample(
      String image, String volume, int round, boolean discarded, boolean profile, Path directory)
      throws Exception {
    String name = profile ? "profile-agent" : "sample-" + round;
    double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    try (GenericContainer<?> application =
            container(image, volume)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
                .withCommand(arguments(profile).toArray(String[]::new))
                .withExposedPorts(8080)
                .withStartupAttempts(1)
                .waitingFor(
                    Wait.forLogMessage(".*Started SpringbootApplication in.*", 1)
                        .withStartupTimeout(TIMEOUT));
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
      try {
        long started = System.nanoTime();
        application.start();
        HttpResponse<String> response =
            client.send(
                HttpRequest.newBuilder(
                        URI.create(
                            "http://"
                                + application.getHost()
                                + ":"
                                + application.getMappedPort(8080)
                                + "/greeting"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(UTF_8));
        double http = (System.nanoTime() - started) / 1_000_000_000.0;
        Instant cutoff = Instant.now();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hi!");
        assertThat(Double.isFinite(http)).isTrue();
        assertThat(http).isPositive();
        String logs = application.getLogs();
        if (profile) {
          assertThat(logs.indexOf("Startup recording active before telemetry agent"))
              .isGreaterThanOrEqualTo(0)
              .isLessThan(logs.indexOf("opentelemetry-javaagent - version:"));
        }
        StartupTimings timings = StartupTimings.parse(logs);
        assertThat(logs).contains("opentelemetry-javaagent - version:");
        if (profile) {
          var dump =
              application.execInContainer(
                  "jcmd", "1", "JFR.dump", "name=startup", "filename=/benchmark/startup.jfr");
          assertThat(dump.getExitCode()).isZero();
          Path recording = directory.resolve("agent.jfr");
          application.copyFileFromContainer("/benchmark/startup.jfr", recording.toString());
          StartupProfile.summarize(recording, cutoff, directory.resolve("agent-profile.txt"));
        }
        return new AgentStartupReport.Sample(
            round, discarded, "ok", timings.jvmSeconds(), timings.springSeconds(), http, load);
      } catch (Throwable t) {
        if (!profile) {
          new AgentStartupReport(directory)
              .append(
                  new AgentStartupReport.Sample(
                      round, discarded, "failed", Double.NaN, Double.NaN, Double.NaN, load));
        }
        throw t;
      } finally {
        if (application.getContainerId() != null) {
          Files.writeString(directory.resolve(name + ".log"), application.getLogs(), UTF_8);
        }
      }
    }
  }

  static List<String> arguments(boolean profile) {
    List<String> arguments = new ArrayList<>(List.of("-Xmx512m"));
    if (profile) {
      arguments.add("-XX:FlightRecorderOptions=stackdepth=256");
      arguments.add("-javaagent:/benchmark/startup-recorder.jar=/benchmark/startup.jfr");
    }
    arguments.add("-javaagent:/benchmark/agent.jar");
    arguments.addAll(
        List.of("-cp", "/app/resources:/app/classes:/app/libs/*", AotStartupBenchmark.APPLICATION));
    return arguments;
  }

  private static GenericContainer<?> container(String image, String volume) {
    return AotStartupBenchmark.container(image, volume)
        .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCpusetCpus("0,1"));
  }

  private static Path agent() {
    Path path = Path.of(required("agent")).toAbsolutePath();
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Agent JAR does not exist: " + path);
    }
    return path;
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

  private static void saveMetadata(Path directory, Properties metadata) throws Exception {
    try (OutputStream out = Files.newOutputStream(directory.resolve("metadata.properties"))) {
      metadata.store(out, "Ordinary Java agent startup benchmark");
    }
  }
}
