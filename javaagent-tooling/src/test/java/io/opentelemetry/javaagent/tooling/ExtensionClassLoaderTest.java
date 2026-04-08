/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtensionClassLoaderTest {
  private static final File AGENT_FILE = new File("/agent.jar");

  @Test
  void testParseLocation(@TempDir Path outputDir) throws Exception {
    String jarPath1 = createJar("test-1.jar", outputDir);
    String jarPath2 = createJar("test-2.jar", outputDir);
    // test that non-jar file is skipped
    Files.createFile(outputDir.resolve("test.txt"));
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1, AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
    {
      // empty paths are ignored
      List<URL> result = ExtensionClassLoader.parseLocation("," + jarPath1 + ",,,", AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1 + "," + jarPath2, AGENT_FILE);
      assertThat(result.size()).isEqualTo(2);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(outputDir.toString(), AGENT_FILE);
      assertThat(result.size()).isEqualTo(2);
    }
    {
      List<URL> result =
          ExtensionClassLoader.parseLocation(
              outputDir + "," + jarPath1 + "," + jarPath2, AGENT_FILE);
      assertThat(result.size()).isEqualTo(4);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation("/anydir", AGENT_FILE);
      assertThat(result.size()).isEqualTo(0);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation("/anyfile.jar", AGENT_FILE);
      assertThat(result.size()).isEqualTo(0);
    }
    {
      List<URL> result = ExtensionClassLoader.parseLocation(jarPath1 + ",/anyfile.jar", AGENT_FILE);
      assertThat(result.size()).isEqualTo(1);
    }
  }

  private static String createJar(String name, Path directory) throws Exception {
    Path jarPath = directory.resolve(name);
    createJar(jarPath);
    return jarPath.toAbsolutePath().toString();
  }

  private static void createJar(Path path) throws Exception {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      // empty jar
    }
  }
}
