/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RestletInstrumentationModuleTest {

  @ParameterizedTest
  @MethodSource("manifests")
  void matchesManifestFallback(String manifest, boolean expected, @TempDir Path tempDir)
      throws IOException {
    Path jar = tempDir.resolve("restlet.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("org/restlet/Request.class"));
      output.closeEntry();
      if (manifest != null) {
        output.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
        output.write(manifest.getBytes(UTF_8));
        output.closeEntry();
      }
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      assertThat(loader.getResource("META-INF/maven/org.restlet/org.restlet/pom.properties"))
          .isNull();
      assertThat(new RestletInstrumentationModule().classLoaderMatcher().matches(loader))
          .isEqualTo(expected);
    }
  }

  private static Stream<Arguments> manifests() {
    return Stream.of(
        argumentSet("minimum supported", manifest("org.restlet", "2.5.0"), true),
        argumentSet("later supported", manifest("org.restlet", "2.7.0"), true),
        argumentSet("older version", manifest("org.restlet", "2.4.4"), false),
        argumentSet("release candidate", manifest("org.restlet", "2.5.0-RC1"), false),
        argumentSet("next major", manifest("org.restlet", "3.0.0-m1"), false),
        argumentSet("missing manifest", null, false),
        argumentSet(
            "missing version",
            "Manifest-Version: 1.0\nBundle-SymbolicName: org.restlet\n\n",
            false),
        argumentSet(
            "missing symbolic name", "Manifest-Version: 1.0\nBundle-Version: 2.5.0\n\n", false),
        argumentSet("malformed version", manifest("org.restlet", "not-a-version"), false),
        argumentSet("malformed manifest", "not a manifest\n\n", false),
        argumentSet("other bundle", manifest("other.bundle", "2.5.0"), false));
  }

  private static String manifest(String symbolicName, String version) {
    return "Manifest-Version: 1.0\nBundle-SymbolicName: "
        + symbolicName
        + "\nBundle-Version: "
        + version
        + "\n\n";
  }
}
