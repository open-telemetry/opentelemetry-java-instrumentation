/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarDetailsTest {

  @Test
  void forUrl_handlesPathWithSpaces(@TempDir Path tempDir) throws IOException {
    Path dirWithSpaces = Files.createDirectories(tempDir.resolve("dir with spaces"));
    Path jarPath = dirWithSpaces.resolve("test.jar");

    Manifest manifest = new Manifest();
    Attributes mainAttributes = manifest.getMainAttributes();
    mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    mainAttributes.put(Attributes.Name.IMPLEMENTATION_TITLE, "Test Title");
    mainAttributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, "Test Vendor");
    try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      // empty jar with manifest only
    }

    URL url = jarPath.toUri().toURL();
    assertThat(url.toExternalForm()).contains("%20");

    JarDetails details = JarDetails.forUrl(url);

    assertThat(details.packageDescription()).isEqualTo("Test Title by Test Vendor");
    assertThat(details.computeSha1()).isNotEmpty();
  }
}
