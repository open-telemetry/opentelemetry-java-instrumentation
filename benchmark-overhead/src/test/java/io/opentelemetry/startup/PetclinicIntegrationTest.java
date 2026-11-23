/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class PetclinicIntegrationTest {
  @TempDir Path directory;

  @Test
  void discoversHelperWithoutChangingSeededResponseAndCapturesJfr() throws Exception {
    Path annotations = directory.resolve("annotations.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(annotations))) {
      jar.putNextEntry(new JarEntry("io/opentelemetry/instrumentation/annotations/WithSpan.class"));
      jar.closeEntry();
    }
    Path helper = Path.of(System.getProperty("petclinic.helper.jar"));
    try (var fixture = PetclinicStartupFixture.start(annotations, helper);
        var client = HttpClient.newHttpClient()) {
      String original;
      try (var app = application(fixture, false)) {
        app.start();
        var response = get(client, app, PetclinicStartupFixture.READINESS_PATH);
        PetclinicStartupFixture.validateResponse(response.statusCode(), response.body());
        assertThat(response.headers().firstValue("X-Startup-JVM-Uptime-Millis")).isEmpty();
        original = response.body();
      }
      try (var app = application(fixture, true)) {
        app.start();
        var response = get(client, app, PetclinicStartupFixture.READINESS_PATH);
        PetclinicStartupFixture.validateResponse(response.statusCode(), response.body());
        assertThat(response.body()).isEqualTo(original);
        assertThat(
                Long.parseLong(
                    response.headers().firstValue("X-Startup-JVM-Uptime-Millis").orElseThrow()))
            .isPositive();
        assertThat(response.headers().firstValue("X-Startup-JVM-Version").orElseThrow())
            .startsWith("25.");
        var other = get(client, app, "/petclinic/api/owners/2");
        assertThat(other.statusCode()).isEqualTo(200);
        assertThat(other.headers().firstValue("X-Startup-JVM-Uptime-Millis")).isEmpty();
        assertThat(other.headers().firstValue("X-Startup-JVM-Version")).isEmpty();
        assertThat(app.execInContainer("jcmd", "1", "JFR.start", "name=verify").getExitCode())
            .isZero();
        assertThat(
                app.execInContainer(
                        "jcmd", "1", "JFR.dump", "name=verify", "filename=/tmp/verify.jfr")
                    .getExitCode())
            .isZero();
        Path recording = directory.resolve("verify.jfr");
        app.copyFileFromContainer("/tmp/verify.jfr", recording.toString());
        assertThat(Files.size(recording)).isPositive();
      }
    }
  }

  private static GenericContainer<?> application(PetclinicStartupFixture fixture, boolean helper) {
    var app =
        new GenericContainer<>(DockerImageName.parse(PetclinicStartupFixture.IMAGE))
            .withExposedPorts(9966)
            .waitingFor(
                Wait.forHttp("/petclinic/actuator/health")
                    .forPort(9966)
                    .withStartupTimeout(Duration.ofMinutes(3)));
    fixture.configure(app);
    if (helper) {
      app.withCommand(
          "java",
          "-Dloader.path=/app/annotations,/app/startup-helper",
          "-cp",
          "/app/spring-petclinic-rest.jar",
          "org.springframework.boot.loader.launch.PropertiesLauncher");
    }
    return app;
  }

  private static HttpResponse<String> get(HttpClient client, GenericContainer<?> app, String path)
      throws Exception {
    var uri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(9966) + path);
    return client.send(
        HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
