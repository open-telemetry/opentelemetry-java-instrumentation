/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleClassLoaderServiceTest {
  @TempDir Path directory;

  @Test
  void reusesAndClosesAnIsolatedInstrumentationLoader() throws IOException {
    Path moduleJar = directory.resolve("module.jar");
    Path toolingJar = directory.resolve("tooling.jar");
    writeJar(moduleJar, "module-marker.txt");
    writeJar(toolingJar, "tooling-marker.txt");
    Project project = ProjectBuilder.builder().build();

    try (MuzzleClassLoaderService service =
        project
            .getGradle()
            .getSharedServices()
            .registerIfAbsent(
                "muzzleClassLoader",
                MuzzleClassLoaderService.class,
                spec -> {
                  spec.getParameters().getModuleJar().set(moduleJar.toFile());
                  spec.getParameters().getToolingJar().set(toolingJar.toFile());
                })
            .get()) {
      ClassLoader loader = service.getClassLoader();

      assertThat(service.getClassLoader()).isSameAs(loader);
      assertThat(loader.getParent()).isSameAs(ClassLoader.getPlatformClassLoader());
      assertThat(loader.getResource("module-marker.txt")).isNotNull();
      assertThat(loader.getResource("tooling-marker.txt")).isNotNull();
      assertThatThrownBy(() -> loader.loadClass("org.gradle.api.Project"))
          .isInstanceOf(ClassNotFoundException.class);

      service.close();
      service.close();

      assertThat(loader.getResource("module-marker.txt")).isNull();
      assertThat(loader.getResource("tooling-marker.txt")).isNull();
      assertThat(service.getClassLoader()).isNotSameAs(loader);
      assertThat(service.getClassLoader().getResource("module-marker.txt")).isNotNull();
    }
  }

  private static void writeJar(Path file, String entry) throws IOException {
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
      jar.putNextEntry(new JarEntry(entry));
      jar.closeEntry();
    }
  }
}
