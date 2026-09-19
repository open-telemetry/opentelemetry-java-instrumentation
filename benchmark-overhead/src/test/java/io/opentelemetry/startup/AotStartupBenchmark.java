/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

@EnabledIfSystemProperty(named = "aot.benchmark.enabled", matches = "true")
class AotStartupBenchmark {
  private static final String IMAGE =
      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/"
          + "smoke-test-spring-boot:jdk25-20251116.19402383847";
  private static final String APPLICATION =
      "io.opentelemetry.smoketest.springboot.SpringbootApplication";
  private static final long MEMORY_BYTES = 1024L * 1024 * 1024;
  private static final long NANO_CPUS = 2_000_000_000L;
  private static final Duration TIMEOUT = Duration.ofMinutes(2);

  @Test
  void compareStartup() throws Exception {
    Path agent = Path.of(requiredProperty("aot.benchmark.agent")).toAbsolutePath();
    if (!Files.isRegularFile(agent)) {
      throw new IllegalArgumentException("Agent JAR does not exist: " + agent);
    }
    int samples = count("aot.benchmark.samples", 1);
    int warmups = count("aot.benchmark.warmups", 0);
    String runId = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID();
    Path directory = Path.of(requiredProperty("aot.benchmark.output")).resolve(runId);
    Files.createDirectories(directory);
    StartupReport report = new StartupReport(directory);
    Properties metadata = metadata(agent, samples, warmups);
    metadata.setProperty("status", "incomplete");
    saveMetadata(directory, metadata);
    System.out.println("AOT startup results: " + directory);

    List<StartupSample> observations = new ArrayList<>();
    DockerClient docker = DockerClientFactory.instance().client();
    String volume = "otel-aot-startup-" + UUID.randomUUID();
    docker.createVolumeCmd().withName(volume).exec();
    try {
      String imageId = prepare(docker, volume, agent, directory, metadata);
      saveMetadata(directory, metadata);
      for (Variant variant : List.of(Variant.AOT_NO_AGENT, Variant.AOT_AGENT)) {
        sample(imageId, volume, variant, 0, 0, true, true, directory, observations, report);
      }
      for (int round = 0; round < warmups + samples; round++) {
        for (int order = 0; order < Variant.values().length; order++) {
          Variant variant = Variant.values()[(round + order) % Variant.values().length];
          sample(
              imageId,
              volume,
              variant,
              round + 1,
              order + 1,
              round < warmups,
              false,
              directory,
              observations,
              report);
        }
      }
      report.writeSummary(observations, metadata);
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
        container(IMAGE, volume)
            .withCopyFileToContainer(MountableFile.forHostPath(agent), "/tmp/agent.jar")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("aot-startup/prepare.sh"), "/tmp/prepare.sh")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/bash"))
            .withCommand(
                "-c", "sed -i 's/\\r$//' /tmp/prepare.sh && exec /bin/bash /tmp/prepare.sh")
            .waitingFor(
                Wait.forLogMessage(".*BENCHMARK_CACHES_READY.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(6)))) {
      try {
        preparation.start();
        var image = docker.inspectImageCmd(preparation.getContainerInfo().getImageId()).exec();
        String imageId = image.getId();
        metadata.setProperty("image.id", imageId);
        metadata.setProperty("image.digests", String.valueOf(image.getRepoDigests()));
        metadata.setProperty("image.architecture", image.getArch());
        metadata.setProperty("image.os", image.getOs());
        for (String file :
            List.of(
                "java-version.log",
                "spring-version.log",
                "cache.properties",
                "no-agent-record.log",
                "no-agent-create.log",
                "agent-record.log",
                "agent-create.log")) {
          preparation.copyFileFromContainer(
              "/benchmark/" + file, directory.resolve(file).toString());
        }
        String version = Files.readString(directory.resolve("java-version.log"), UTF_8).strip();
        assertThat(version).contains("25.").doesNotContain("OpenJ9");
        metadata.setProperty("jvm.version", version);
        metadata.setProperty(
            "spring.version",
            Files.readString(directory.resolve("spring-version.log"), UTF_8).strip());
        try (InputStream in = Files.newInputStream(directory.resolve("cache.properties"))) {
          metadata.load(in);
        }
        return imageId;
      } finally {
        if (preparation.getContainerId() != null) {
          Files.writeString(directory.resolve("preparation.log"), preparation.getLogs(), UTF_8);
        }
      }
    }
  }

  private static void sample(
      String image,
      String volume,
      Variant variant,
      int round,
      int order,
      boolean discarded,
      boolean preflight,
      Path directory,
      List<StartupSample> observations,
      StartupReport report)
      throws Exception {
    String name =
        preflight ? "preflight-" + variant.id() : round + "-" + order + "-" + variant.id();
    try (GenericContainer<?> application =
            container(image, volume)
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
                .withCommand(arguments(variant, preflight).toArray(String[]::new))
                .withExposedPorts(8080)
                .withStartupAttempts(1)
                .waitingFor(
                    Wait.forLogMessage(".*Started SpringbootApplication in.*", 1)
                        .withStartupTimeout(TIMEOUT));
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
      boolean success = false;
      int writtenSamples = observations.size();
      try {
        long started = System.nanoTime();
        application.start();
        HttpRequest request =
            HttpRequest.newBuilder(
                    URI.create(
                        "http://"
                            + application.getHost()
                            + ":"
                            + application.getMappedPort(8080)
                            + "/greeting"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        double httpSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hi!");
        String logs = application.getLogs();
        StartupTimings timings = StartupTimings.parse(logs);
        if (variant.agent()) {
          assertThat(logs).contains("opentelemetry-javaagent - version:");
        } else {
          assertThat(logs).doesNotContain("opentelemetry-javaagent - version:");
        }
        if (preflight) {
          assertThat(logs)
              .contains("Opened AOT cache")
              .contains("Using AOT-linked classes: true")
              .contains(APPLICATION + " source: shared objects file");
          if (variant.agent()) {
            assertThat(logs)
                .contains(
                    "Transformed io.opentelemetry.smoketest.springboot.controller.WebController")
                .doesNotContain(
                    "Instrumentation.appendToBootstrapClassLoaderSearch has been called");
          }
        } else {
          observations.add(
              new StartupSample(
                  variant,
                  round,
                  order,
                  discarded,
                  "ok",
                  timings.springSeconds(),
                  timings.jvmSeconds(),
                  httpSeconds));
          System.out.printf(
              "%s %s: JVM %.3f s, Spring %.3f s%n",
              name,
              discarded ? "discarded" : "measured",
              timings.jvmSeconds(),
              timings.springSeconds());
        }
        success = true;
      } finally {
        if (!success && !preflight) {
          observations.add(
              new StartupSample(
                  variant, round, order, discarded, "failed", Double.NaN, Double.NaN, Double.NaN));
        }
        if (observations.size() > writtenSamples) {
          report.writeSamples(observations.subList(writtenSamples, observations.size()));
        }
        if (application.getContainerId() != null) {
          Files.writeString(directory.resolve(name + ".log"), application.getLogs(), UTF_8);
        }
      }
    }
  }

  private static GenericContainer<?> container(String image, String volume) {
    return new GenericContainer<>(DockerImageName.parse(image))
        .withEnv("JAVA_TOOL_OPTIONS", "")
        .withEnv("JDK_JAVA_OPTIONS", "")
        .withEnv("_JAVA_OPTIONS", "")
        .withEnv("OTEL_TRACES_EXPORTER", "none")
        .withEnv("OTEL_METRICS_EXPORTER", "none")
        .withEnv("OTEL_LOGS_EXPORTER", "none")
        .withCreateContainerCmdModifier(
            cmd ->
                cmd.getHostConfig()
                    .withBinds(new Bind(volume, new Volume("/benchmark")))
                    .withMemory(MEMORY_BYTES)
                    .withMemorySwap(MEMORY_BYTES)
                    .withNanoCPUs(NANO_CPUS));
  }

  static List<String> arguments(Variant variant, boolean preflight) {
    List<String> arguments = new ArrayList<>();
    arguments.add("-Xmx512m");
    if (variant.agent()) {
      arguments.add("--add-modules=java.instrument");
      arguments.add("-Xbootclasspath/a:/benchmark/agent.jar");
      arguments.add("-javaagent:/benchmark/agent.jar");
      arguments.add("-Dotel.javaagent.debug=" + preflight);
    }
    if (variant.aot()) {
      arguments.add("-XX:AOTMode=on");
      arguments.add("-XX:AOTCache=/benchmark/" + (variant.agent() ? "agent" : "no-agent") + ".aot");
    }
    if (preflight) {
      arguments.add("-XX:+UnlockDiagnosticVMOptions");
      arguments.add("-XX:+VerifySharedSpaces");
      arguments.add("-Xlog:aot=debug,class+load=info");
      arguments.add("-Djdk.instrument.traceUsage=true");
    }
    arguments.add("-cp");
    arguments.add("/benchmark/application.jar:/app/libs/*");
    arguments.add(APPLICATION);
    return arguments;
  }

  private static Properties metadata(Path agent, int samples, int warmups) throws Exception {
    Properties metadata = new Properties();
    metadata.setProperty("image.reference", IMAGE);
    metadata.setProperty("agent.path", agent.toString());
    try (JarFile jar = new JarFile(agent.toFile())) {
      String version = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
      if (version == null) {
        throw new IllegalArgumentException("Agent JAR has no Implementation-Version");
      }
      metadata.setProperty("agent.version", version);
    }
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(agent)) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) {
        digest.update(buffer, 0, count);
      }
    }
    metadata.setProperty("agent.sha256", HexFormat.of().formatHex(digest.digest()));
    metadata.setProperty("repository.revision", git("rev-parse", "HEAD"));
    metadata.setProperty(
        "repository.dirty", Boolean.toString(!git("status", "--porcelain").isEmpty()));
    metadata.setProperty("samples", Integer.toString(samples));
    metadata.setProperty("warmups", Integer.toString(warmups));
    metadata.setProperty("cpus", "2");
    metadata.setProperty("memory.bytes", Long.toString(MEMORY_BYTES));
    metadata.setProperty("heap", "-Xmx512m");
    metadata.setProperty("host.os", System.getProperty("os.name"));
    metadata.setProperty("host.architecture", System.getProperty("os.arch"));
    metadata.setProperty(
        "exporters", "traces=none, metrics=none, logs=none; instrumentation enabled");
    for (Variant variant : Variant.values()) {
      metadata.setProperty(
          "command." + variant.id(), "java " + String.join(" ", arguments(variant, false)));
    }
    metadata.setProperty(
        "reproduction.command",
        "From benchmark-overhead: ./gradlew aotStartupBenchmark "
            + "\"-PaotBenchmarkAgentJar="
            + agent
            + "\" -PaotBenchmarkSamples="
            + samples
            + " -PaotBenchmarkWarmups="
            + warmups);
    return metadata;
  }

  private static String git(String... arguments) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of("git", "--no-pager"));
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), UTF_8).strip();
    if (process.waitFor() != 0) {
      throw new IOException("Git metadata command failed: " + output);
    }
    return output;
  }

  private static void saveMetadata(Path directory, Properties metadata) throws IOException {
    try (OutputStream out = Files.newOutputStream(directory.resolve("metadata.properties"))) {
      metadata.store(out, "AOT startup benchmark");
    }
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required benchmark property: " + name);
    }
    return value;
  }

  private static int count(String name, int minimum) {
    int value = Integer.parseInt(requiredProperty(name));
    if (value < minimum) {
      throw new IllegalArgumentException(name + " must be at least " + minimum);
    }
    return value;
  }
}
