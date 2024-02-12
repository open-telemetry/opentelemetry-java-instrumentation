/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.AttributesAssert;
import io.opentelemetry.semconv.ResourceAttributes;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JarResourceDetectorsTest {

  @Mock ConfigProperties config;

  @Test
  void createResource_empty() {
    JarServiceNameDetector serviceNameProvider =
        new JarServiceNameDetector(
            () -> new String[0], prop -> null, JarResourceDetectorsTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  @Test
  void createResource_noJarFileInArgs() {
    String[] args = new String[] {"-Dtest=42", "-Xmx666m", "-jar"};
    JarServiceNameDetector serviceNameProvider =
        new JarServiceNameDetector(() -> args, prop -> null, JarResourceDetectorsTest::failPath);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  @ParameterizedTest(name = "[{index}]: {0} -> {1}, {2}")
  @CsvSource({
    "my-service, my-service, ",
    "my-service.jar0, my-service.jar0, ",
    "my-service.jar, my-service, ",
    "my-service-1.0.0-SNAPSHOT, my-service-1.0.0-SNAPSHOT, ",
    "my-service-1.0.0-SNAPSHOT.jar, my-service, 1.0.0-SNAPSHOT",
    "my-service-v1.0.0-SNAPSHOT.jar, my-service, v1.0.0-SNAPSHOT",
    "my-service-1.0.0, my-service-1.0.0, ",
    "my-service-1.0.0.jar, my-service, 1.0.0",
    "my-service-v1.0.0.jar, my-service, v1.0.0",
    "my-service2.3-1.0.0.jar, my-service2.3, 1.0.0",
    "my-service-2.3-1.0.0.jar, my-service, 2.3-1.0.0",
    "my_service_2.3_1.0.0.jar, my_service, 2.3_1.0.0",
  })
  void createResource_processHandleJar(
      String jar, String expectedServiceName, @Nullable String expectedServiceVersion) {
    String path = Paths.get("path", "to", "app", jar).toString();
    String[] args = new String[] {"-Dtest=42", "-Xmx666m", "-jar", path, "abc", "def"};
    JarServiceNameDetector serviceNameProvider =
        new JarServiceNameDetector(() -> args, prop -> null, JarResourceDetectorsTest::failPath);
    JarServiceVersionDetector serviceVersionProvider =
        new JarServiceVersionDetector(() -> args, prop -> null, JarResourceDetectorsTest::failPath);

    Resource resource =
        serviceNameProvider
            .createResource(config)
            .merge(serviceVersionProvider.createResource(config));

    AttributesAssert attributesAssert = assertThat(resource.getAttributes());
    attributesAssert.containsEntry(ResourceAttributes.SERVICE_NAME, expectedServiceName);

    if (expectedServiceVersion == null) {
      attributesAssert.doesNotContainKey(ResourceAttributes.SERVICE_VERSION);
    } else {
      attributesAssert.containsEntry(ResourceAttributes.SERVICE_VERSION, expectedServiceVersion);
    }
  }

  @ParameterizedTest
  @ArgumentsSource(SunCommandLineProvider.class)
  void createResource_sunCommandLine(String commandLine, Path jarPath) {
    Function<String, String> getProperty =
        key -> "sun.java.command".equals(key) ? commandLine : null;
    Predicate<Path> fileExists = jarPath::equals;

    JarServiceNameDetector serviceNameProvider =
        new JarServiceNameDetector(() -> new String[0], getProperty, fileExists);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes())
        .hasSize(1)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "my-service");
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/7567
  @Test
  void createResource_sunCommandLineProblematicArgs() {
    Function<String, String> getProperty =
        key -> key.equals("sun.java.command") ? "one C:/two" : null;
    Predicate<Path> fileExists = path -> false;

    JarServiceNameDetector serviceNameProvider =
        new JarServiceNameDetector(() -> new String[0], getProperty, fileExists);

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

  private static boolean failPath(Path file) {
    throw new AssertionError("Unexpected call to Files.isRegularFile()");
  }
}
