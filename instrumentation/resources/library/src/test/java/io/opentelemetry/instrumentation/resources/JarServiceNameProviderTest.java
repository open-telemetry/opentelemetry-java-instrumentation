/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.nio.file.Path;
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
class JarServiceNameProviderTest {

  @Mock ConfigProperties config;

  @Test
  void createResource_empty() {
    JarServiceNameProvider serviceNameProvider =
        new JarServiceNameProvider(() -> new String[0], prop -> null, file -> false);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  @Test
  void createResource_noJarFileInArgs() {
    String[] args = new String[] {"-Dtest=42", "-Xmx666m", "-jar"};
    JarServiceNameProvider serviceNameProvider =
        new JarServiceNameProvider(() -> args, prop -> null, file -> false);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes()).isEmpty();
  }

  @Test
  void createResource_processHandleJar() {
    String[] args =
        new String[] {"-Dtest=42", "-Xmx666m", "-jar", "/path/to/app/my-service.jar", "abc", "def"};
    JarServiceNameProvider serviceNameProvider =
        new JarServiceNameProvider(() -> args, prop -> null, file -> false);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes())
        .hasSize(1)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "my-service");
  }

  @Test
  void createResource_processHandleJarWithoutExtension() {
    String[] args = new String[] {"-Dtest=42", "-Xmx666m", "-jar", "/path/to/app/my-service"};
    JarServiceNameProvider serviceNameProvider =
        new JarServiceNameProvider(() -> args, prop -> null, file -> false);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes())
        .hasSize(1)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "my-service");
  }

  @ParameterizedTest
  @ArgumentsSource(SunCommandLineProvider.class)
  void createResource_sunCommandLine(String commandLine, String jarPath) {
    Function<String, String> getProperty =
        key -> "sun.java.command".equals(key) ? commandLine : null;
    Predicate<Path> fileExists = file -> jarPath.equals(file.toString());

    JarServiceNameProvider serviceNameProvider =
        new JarServiceNameProvider(() -> new String[0], getProperty, fileExists);

    Resource resource = serviceNameProvider.createResource(config);

    assertThat(resource.getAttributes())
        .hasSize(1)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "my-service");
  }

  static final class SunCommandLineProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          arguments("/path/to/my-service.jar", "/path/to/my-service.jar"),
          arguments(
              "/path to app/with spaces/my-service.jar 1 2 3",
              "/path to app/with spaces/my-service.jar"),
          arguments(
              "/path to app/with spaces/my-service 1 2 3", "/path to app/with spaces/my-service"));
    }
  }
}
