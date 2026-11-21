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
    String workload = System.getProperty("startup.benchmark.workload", "smoke");
    if (!workload.equals("smoke") && !workload.equals("petclinic")) {
      throw new IllegalArgumentException("Unknown startup workload: " + workload);
    }
    Path annotations =
        workload.equals("petclinic") ? Path.of(required("annotations")).toAbsolutePath() : null;
    Path helper =
        workload.equals("petclinic") ? Path.of(required("petclinicHelper")).toAbsolutePath() : null;
    try (PetclinicStartupFixture petclinic =
        annotations == null ? null : PetclinicStartupFixture.start(annotations, helper)) {
      measureStartup(petclinic, annotations);
    }
  }

  private static void measureStartup(PetclinicStartupFixture petclinic, Path annotations)
      throws Exception {
    List<Configuration> configurations = new ArrayList<>();
    configurations.add(new Configuration("no-agent", null, "none"));
    String baselineAgent = System.getProperty("startup.benchmark.baselineAgent", "");
    if (!baselineAgent.isBlank()) {
      configurations.add(
          new Configuration("baseline-agent", agent(baselineAgent), required("baselineRevision")));
    }
    configurations.add(new Configuration("agent", agent(required("agent")), required("revision")));
    Path recorder = recorder();
    Path fixture = petclinic == null ? fixtureClass() : null;
    int samples = count("samples", 1);
    int warmups = count("warmups", 0);
    Path directory =
        Path.of(required("output"))
            .resolve(Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID());
    Files.createDirectories(directory);
    Properties metadata = new Properties();
    metadata.setProperty("status", "incomplete");
    metadata.setProperty("workload", petclinic == null ? "smoke" : "petclinic");
    metadata.setProperty(
        "image.reference",
        petclinic == null ? StartupFixture.IMAGE : PetclinicStartupFixture.IMAGE);
    if (petclinic != null) {
      metadata.setProperty("application.version", "spring-petclinic-rest 2.4.2");
      metadata.setProperty("application.image.tag", "petclinic-rest-base:20230601125442");
      metadata.setProperty("application.profiles", "postgresql,spring-data-jpa");
      metadata.setProperty("database.image.reference", PetclinicStartupFixture.DATABASE_IMAGE);
      metadata.setProperty("database.version", "postgres 9.6.22");
      metadata.setProperty("readiness", PetclinicStartupFixture.READINESS_PATH + " seeded owner 1");
      metadata.setProperty("annotations.sha256", checksum(annotations));
      metadata.setProperty("annotations.path", annotations.toString());
      metadata.setProperty("helper.sha256", checksum(petclinic.helper()));
      metadata.setProperty("helper.path", petclinic.helper().toString());
    }
    metadata.setProperty(
        "timing",
        "application JVM uptime at the first successful readiness response; includes JVM/agent initialization and application/request work before sampling; excludes container setup, database provisioning, and return network transit; probe polling affects when the successful request reaches the application");
    metadata.setProperty("samples", Integer.toString(samples));
    metadata.setProperty("warmups", Integer.toString(warmups));
    metadata.setProperty("resources", "quota=2 CPUs; memory=1 GiB; heap=512 MiB");
    metadata.setProperty(
        "exporters", "traces=none, metrics=none, logs=none; instrumentation enabled with agent");
    for (Configuration configuration : configurations) {
      String name = configuration.name();
      metadata.setProperty(
          "command." + name,
          "java "
              + String.join(
                  " ", arguments(configuration.agent() != null, false, petclinic != null)));
      metadata.setProperty(name + ".source.revision", configuration.revision());
      if (configuration.agent() != null) {
        try (JarFile jar = new JarFile(configuration.agent().toFile())) {
          metadata.setProperty(
              name + ".version",
              jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
        metadata.setProperty(name + ".sha256", checksum(configuration.agent()));
        metadata.setProperty(name + ".path", configuration.agent().toString());
        metadata.setProperty(
            "command.profile." + name,
            "java " + String.join(" ", arguments(true, true, petclinic != null)));
      }
    }
    metadata.setProperty("recorder.sha256", checksum(recorder));
    if (fixture != null) {
      metadata.setProperty("fixture.sha256", checksum(fixture));
    }
    metadata.setProperty("recorder.path", recorder.toString());
    metadata.setProperty("source.revision", System.getProperty("startup.benchmark.revision"));
    metadata.setProperty("measurement.order", "rotating configuration order each round");
    saveMetadata(directory, metadata);

    DockerClient docker = DockerClientFactory.instance().client();
    List<AgentStartupReport.Sample> observations = new ArrayList<>();
    AgentStartupReport report = new AgentStartupReport(directory);
    for (int round = 0; round < warmups + samples; round++) {
      for (int offset = 0; offset < configurations.size(); offset++) {
        Configuration configuration = configurations.get((round + offset) % configurations.size());
        AgentStartupReport.Sample observation =
            sample(
                configuration,
                recorder,
                fixture,
                round,
                round < warmups,
                false,
                directory,
                metadata,
                docker,
                petclinic);
        observations.add(observation);
        report.append(observation);
        assertThat(observation.status()).isEqualTo("ok");
      }
    }
    report.summarize(observations);
    for (Configuration configuration : configurations) {
      if (configuration.agent() != null) {
        sample(
            configuration,
            recorder,
            fixture,
            -1,
            false,
            true,
            directory,
            metadata,
            docker,
            petclinic);
      }
    }
    metadata.setProperty("status", "complete");
    saveMetadata(directory, metadata);
  }

  private static AgentStartupReport.Sample sample(
      Configuration configuration,
      Path recorder,
      Path fixture,
      int round,
      boolean discarded,
      boolean profile,
      Path directory,
      Properties metadata,
      DockerClient docker,
      PetclinicStartupFixture petclinic)
      throws Exception {
    boolean withAgent = configuration.agent() != null;
    String name =
        profile
            ? "profile-" + configuration.name()
            : "sample-" + configuration.name() + "-" + round;
    double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    try (GenericContainer<?> application =
            container(petclinic != null)
                .withCopyFileToContainer(
                    MountableFile.forHostPath(recorder), "/tmp/startup-recorder.jar")
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("java"))
                .withCommand(
                    arguments(withAgent, profile, petclinic != null).toArray(String[]::new))
                .withExposedPorts(petclinic == null ? 8080 : 9966)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(TIMEOUT))
                .withStartupAttempts(1);
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
      try {
        if (petclinic == null) {
          application.withCopyFileToContainer(
              MountableFile.forHostPath(fixture), "/app/classes/" + APPLICATION_CLASS);
        } else {
          petclinic.configure(application);
        }
        if (withAgent) {
          application.withCopyFileToContainer(
              MountableFile.forHostPath(configuration.agent()), "/tmp/agent.jar");
        }
        application.start();
        HttpResponse<String> response = awaitReadiness(application, client, petclinic != null);
        Instant cutoff = Instant.now();
        double startupSeconds =
            uptimeSeconds(response.headers().firstValue(STARTUP_UPTIME_HEADER).orElse(null));
        String jvmVersion =
            petclinic == null
                ? response.headers().firstValue(JVM_VERSION_HEADER).orElse(null)
                : application.execInContainer("java", "-version").getStderr().trim();
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
        if (withAgent) {
          assertThat(logs).contains("opentelemetry-javaagent - version:");
        } else {
          assertThat(logs).doesNotContain("opentelemetry-javaagent - version:");
        }
        if (profile) {
          assertThat(logs.indexOf("Startup recording active before telemetry agent"))
              .isGreaterThanOrEqualTo(0)
              .isLessThan(logs.indexOf("opentelemetry-javaagent - version:"));
          var dump =
              application.execInContainer(
                  "jcmd", "1", "JFR.dump", "name=startup", "filename=/tmp/startup.jfr");
          assertThat(dump.getExitCode()).isZero();
          Path recording = directory.resolve(configuration.name() + ".jfr");
          application.copyFileFromContainer("/tmp/startup.jfr", recording.toString());
          StartupProfile.summarize(
              recording, cutoff, directory.resolve(configuration.name() + "-profile.txt"));
        }
        return new AgentStartupReport.Sample(
            configuration.name(), round, discarded, "ok", startupSeconds, load);
      } catch (Throwable t) {
        if (!profile) {
          new AgentStartupReport(directory)
              .append(
                  new AgentStartupReport.Sample(
                      configuration.name(), round, discarded, "failed", Double.NaN, load));
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
      GenericContainer<?> application, HttpClient client, boolean petclinic) throws Exception {
    URI uri =
        URI.create(
            "http://"
                + application.getHost()
                + ":"
                + application.getMappedPort(petclinic ? 9966 : 8080)
                + (petclinic ? PetclinicStartupFixture.READINESS_PATH : "/greeting"));
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    IOException lastConnectionFailure = null;
    while (System.nanoTime() < deadline) {
      try {
        HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
        if (response.statusCode() == 200) {
          if (petclinic) {
            petclinicStartupSeconds(
                response.statusCode(),
                response.body(),
                response.headers().firstValue(STARTUP_UPTIME_HEADER).orElse(null));
          } else {
            startupSeconds(
                response.statusCode(),
                response.body(),
                response.headers().firstValue(STARTUP_UPTIME_HEADER).orElse(null));
          }
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
        "Timed out waiting for successful "
            + (petclinic ? PetclinicStartupFixture.READINESS_PATH : "/greeting")
            + " readiness",
        lastConnectionFailure);
  }

  static double startupSeconds(int status, String body, String uptimeHeader) {
    if (status != 200 || !"Hi!".equals(body)) {
      throw new IllegalArgumentException(
          "Readiness must return HTTP 200 and body Hi!, got HTTP " + status + " and " + body);
    }
    return uptimeSeconds(uptimeHeader);
  }

  static double petclinicStartupSeconds(int status, String body, String uptimeHeader) {
    PetclinicStartupFixture.validateResponse(status, body);
    return uptimeSeconds(uptimeHeader);
  }

  private static double uptimeSeconds(String uptimeHeader) {
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

  static List<String> arguments(boolean withAgent, boolean profile) {
    return arguments(withAgent, profile, false);
  }

  static List<String> arguments(boolean withAgent, boolean profile, boolean petclinic) {
    List<String> arguments = new ArrayList<>(List.of("-Xmx512m"));
    if (profile) {
      arguments.add("-XX:FlightRecorderOptions=stackdepth=256");
      arguments.add("-javaagent:/tmp/startup-recorder.jar=/tmp/startup.jfr");
    }
    if (withAgent) {
      arguments.add("-javaagent:/tmp/agent.jar");
    }
    if (petclinic) {
      arguments.addAll(
          List.of(
              "-Dloader.path=/app/annotations,/app/startup-helper",
              "-cp",
              "/app/spring-petclinic-rest.jar",
              "org.springframework.boot.loader.PropertiesLauncher"));
    } else {
      arguments.addAll(
          List.of("-cp", "/app/resources:/app/classes:/app/libs/*", StartupFixture.APPLICATION));
    }
    return arguments;
  }

  private static GenericContainer<?> container(boolean petclinic) {
    return new GenericContainer<>(
            DockerImageName.parse(petclinic ? PetclinicStartupFixture.IMAGE : StartupFixture.IMAGE))
        .withEnv("JAVA_TOOL_OPTIONS", "")
        .withEnv("JDK_JAVA_OPTIONS", "")
        .withEnv("_JAVA_OPTIONS", "")
        .withEnv("OTEL_TRACES_EXPORTER", "none")
        .withEnv("OTEL_METRICS_EXPORTER", "none")
        .withEnv("OTEL_LOGS_EXPORTER", "none")
        .withCreateContainerCmdModifier(cmd ->
            cmd.getHostConfig()
                .withMemory(MEMORY_BYTES)
                .withMemorySwap(MEMORY_BYTES)
                .withNanoCPUs(NANO_CPUS));
  }

  private static Path agent(String value) {
    Path path = Path.of(value).toAbsolutePath();
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

  private record Configuration(String name, Path agent, String revision) {}
}
