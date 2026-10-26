/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.DockerClientFactory;

@EnabledIfEnvironmentVariable(named = "STARTUP_VOLUME_CLEANUP_ENABLED", matches = "true")
class AgentStartupVolumeReaperTest {

  @Test
  void removesOnlySessionVolumeAfterAbruptJvmExit() throws Exception {
    DockerClient docker = DockerClientFactory.instance().client();
    String ownedVolume = "otel-startup-owned-" + UUID.randomUUID();
    String unrelatedVolume = "otel-startup-unrelated-" + UUID.randomUUID();
    docker.createVolumeCmd().withName(unrelatedVolume).exec();
    Process child = null;
    try {
      child =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-cp",
                  System.getProperty("java.class.path"),
                  AgentStartupVolumeReaperProcess.class.getName(),
                  ownedVolume)
              .redirectErrorStream(true)
              .start();
      boolean exited = child.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
      if (!exited) {
        child.destroyForcibly();
      }
      assertThat(exited).isTrue();
      assertThat(child.exitValue()).isZero();
      String output = new String(child.getInputStream().readAllBytes(), UTF_8);
      assertThat(output).doesNotContain("Exception");

      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (volumeExists(docker, ownedVolume) && System.nanoTime() < deadline) {
        Thread.sleep(100);
      }
      assertThat(volumeExists(docker, ownedVolume)).isFalse();
      assertThat(volumeExists(docker, unrelatedVolume)).isTrue();
    } finally {
      if (child != null && child.isAlive()) {
        child.destroyForcibly();
        child.waitFor();
      }
      removeVolumeIfPresent(docker, ownedVolume);
      removeVolumeIfPresent(docker, unrelatedVolume);
    }
  }

  private static boolean volumeExists(DockerClient docker, String name) {
    return docker.listVolumesCmd().exec().getVolumes().stream()
        .anyMatch(volume -> name.equals(volume.getName()));
  }

  private static void removeVolumeIfPresent(DockerClient docker, String name) {
    if (volumeExists(docker, name)) {
      docker.removeVolumeCmd(name).exec();
    }
  }
}
