/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
// todo split JarFileDetectorTest and JarServiceNameDetectorTest
class JarServiceNameDetectorTest {

  @Mock ConfigProperties config;

  @Test
  void createResource_empty() {
    String[] processArgs = new String[0];
    Function<String, String> getProperty = prop -> null;
    Predicate<Path> fileExists = JarServiceNameDetectorTest::failPath;
    JarServiceNameDetector serviceNameProvider = getDetector(processArgs, getProperty, fileExists);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  private static JarServiceNameDetector getDetector(
      String[] processArgs, Function<String, String> getProperty, Predicate<Path> fileExists) {
    return new JarServiceNameDetector(
        new MainJarPathFinder(() -> processArgs, getProperty, fileExists));
  }

  @Test
  void createResource_noJarFileInArgs() {
    String[] args = new String[] {"-Dtest=42", "-Xmx666m", "-jar"};
    JarServiceNameDetector serviceNameProvider =
        getDetector(args, prop -> null, JarServiceNameDetectorTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  @Test
  void createResource_processHandleJar() {
    JarServiceNameDetector serviceNameProvider =
        getDetector(getArgs("my-service.jar"), prop -> null, JarServiceNameDetectorTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).hasSize(1).containsEntry(SERVICE_NAME, "my-service");
  }

  @Test
  void createResource_processHandleJarExtraFlag() {
    String path = Paths.get("path", "to", "app", "my-service.jar").toString();
    JarServiceNameDetector serviceNameProvider =
        getDetector(
            new String[] {"-Dtest=42", "-jar", "-Xmx512m", path, "abc", "def"},
            prop -> null,
            JarServiceNameDetectorTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).hasSize(1).containsEntry(SERVICE_NAME, "my-service");
  }

  @Test
  void createResource_processHandleJarWithoutExtension() {
    JarServiceNameDetector serviceNameProvider =
        getDetector(getArgs("my-service"), prop -> null, JarServiceNameDetectorTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).hasSize(1).containsEntry(SERVICE_NAME, "my-service");
  }

  static String[] getArgs(String jarName) {
    String path = Paths.get("path", "to", "app", jarName).toString();
    return new String[] {"-Dtest=42", "-Xmx666m", "-jar", path, "abc", "def"};
  }

  @ParameterizedTest
  @ArgumentsSource(SunCommandLineProvider.class)
  void createResource_sunCommandLine(String commandLine, Path jarPath) {
    Function<String, String> getProperty =
        key -> "sun.java.command".equals(key) ? commandLine : null;
    Predicate<Path> fileExists = jarPath::equals;

    JarServiceNameDetector serviceNameProvider =
        getDetector(new String[0], getProperty, fileExists);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).hasSize(1).containsEntry(SERVICE_NAME, "my-service");
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/7567
  @Test
  void createResource_sunCommandLineProblematicArgs() {
    Function<String, String> getProperty =
        key -> key.equals("sun.java.command") ? "one C:/two" : null;
    Predicate<Path> fileExists = path -> false;

    JarServiceNameDetector serviceNameProvider =
        getDetector(new String[0], getProperty, fileExists);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  static final class SunCommandLineProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      Path path = Paths.get("path", "to", "my-service.jar");
      Path pathWithSpaces = Paths.get("path to app", "with spaces", "my-service.jar");
      Path pathWithoutExtension = Paths.get("path to app", "with spaces", "my-service");
      return Stream.of(
          arguments(path.toString(), path),
          arguments(pathWithSpaces + " 1 2 3", pathWithSpaces),
          arguments(pathWithoutExtension + " 1 2 3", pathWithoutExtension));
    }
  }

  static boolean failPath(Path file) {
    throw new AssertionError("Unexpected call to Files.isRegularFile()");
  }
}
