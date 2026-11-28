/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

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
  @MethodSource("classLoaders")
  void matchesExpectedClasses(String[] classes, boolean expected, @TempDir Path tempDir)
      throws IOException {
    Path jar = tempDir.resolve("restlet.jar");
    createJar(jar, classes);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      assertThat(new RestletInstrumentationModule().classLoaderMatcher().matches(loader))
          .isEqualTo(expected);
    }
  }

  private static void createJar(Path jar, String[] classes) throws IOException {
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      for (String className : classes) {
        output.putNextEntry(new JarEntry(className));
        output.closeEntry();
      }
    }
  }

  private static Stream<Arguments> classLoaders() {
    return Stream.of(
        argumentSet(
            "Restlet 2.4 core classes",
            new String[] {
              "org/restlet/Request.class", "org/restlet/engine/component/ComponentXmlParser.class"
            },
            false),
        argumentSet("Restlet 2.5 core classes", new String[] {"org/restlet/Request.class"}, true),
        argumentSet("missing Restlet Request", new String[0], false));
  }
}
