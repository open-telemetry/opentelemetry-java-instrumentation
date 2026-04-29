/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarDetailsTest {

  @Test
  void forUrl_handlesPathWithSpaces(@TempDir Path tempDir) throws IOException {
    Path dirWithSpaces = Files.createDirectories(tempDir.resolve("dir with spaces"));
    Path jarPath = dirWithSpaces.resolve("test.jar");
    writeJar(jarPath, manifest("Test Title", "Test Vendor"));

    URL url = jarPath.toUri().toURL();
    assertThat(url.toExternalForm()).contains("%20");

    JarDetails details = JarDetails.forUrl(url);

    assertThat(details.packageDescription()).isEqualTo("Test Title by Test Vendor");
    assertThat(details.computeSha1()).isNotEmpty();
  }

  @Test
  void forUrl_handlesEmbeddedJar(@TempDir Path tempDir) throws IOException {
    // Build an inner jar in-memory.
    ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
    try (JarOutputStream innerJar =
        new JarOutputStream(innerBytes, manifest("Inner Title", "Inner Vendor"))) {
      // empty inner jar with manifest only
    }

    // Build an outer war containing the inner jar at WEB-INF/lib/inner.jar.
    Path warPath = tempDir.resolve("outer.war");
    try (JarOutputStream outerJar =
        new JarOutputStream(
            Files.newOutputStream(warPath), manifest("Outer Title", "Outer Vendor"))) {
      outerJar.putNextEntry(new JarEntry("WEB-INF/lib/inner.jar"));
      outerJar.write(innerBytes.toByteArray());
      outerJar.closeEntry();
    }

    URL url = new URL("jar:" + warPath.toUri().toURL() + "!/WEB-INF/lib/inner.jar");
    JarDetails details = JarDetails.forUrl(url);

    assertThat(details.packageDescription()).isEqualTo("Inner Title by Inner Vendor");
    assertThat(details.computeSha1()).isNotEmpty();
  }

  @Test
  void forUrl_handlesEmbeddedJarWithSpaces(@TempDir Path tempDir) throws IOException {
    ByteArrayOutputStream innerBytes = new ByteArrayOutputStream();
    try (JarOutputStream innerJar =
        new JarOutputStream(innerBytes, manifest("Inner Title", "Inner Vendor"))) {
      // empty inner jar with manifest only
    }

    Path dirWithSpaces = Files.createDirectories(tempDir.resolve("dir with spaces"));
    Path warPath = dirWithSpaces.resolve("outer.war");
    try (JarOutputStream outerJar =
        new JarOutputStream(
            Files.newOutputStream(warPath), manifest("Outer Title", "Outer Vendor"))) {
      outerJar.putNextEntry(new JarEntry("WEB-INF/lib/inner.jar"));
      outerJar.write(innerBytes.toByteArray());
      outerJar.closeEntry();
    }

    URL url = new URL("jar:" + warPath.toUri().toURL() + "!/WEB-INF/lib/inner.jar");
    assertThat(url.toExternalForm()).contains("%20");

    JarDetails details = JarDetails.forUrl(url);

    assertThat(details.packageDescription()).isEqualTo("Inner Title by Inner Vendor");
    assertThat(details.computeSha1()).isNotEmpty();
  }

  private static Manifest manifest(String title, String vendor) {
    Manifest manifest = new Manifest();
    Attributes mainAttributes = manifest.getMainAttributes();
    mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mainAttributes.put(Attributes.Name.IMPLEMENTATION_TITLE, title);
    mainAttributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, vendor);
    return manifest;
  }

  private static void writeJar(Path path, Manifest manifest) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      // empty jar with manifest only
    }
  }
}
