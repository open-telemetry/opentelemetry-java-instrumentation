/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HostIdResourceTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("createResourceLinuxCases")
  void createResourceLinux(
      String name, String expectedValue, Function<Path, List<String>> pathReader) {
    HostIdResource hostIdResource = new HostIdResource(() -> "linux", pathReader, null);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceLinuxCases() {
    return Stream.of(
        arguments("default", "test", (Function<Path, List<String>>) path -> singletonList("test")),
        arguments(
            "empty file or error reading",
            null,
            (Function<Path, List<String>>) path -> emptyList()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createResourceWindowsCases")
  void createResourceWindows(
      String name, String expectedValue, Supplier<List<String>> queryWindowsRegistry) {
    HostIdResource hostIdResource =
        new HostIdResource(() -> "Windows 95", null, queryWindowsRegistry);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceWindowsCases() {
    return Stream.of(
        arguments(
            "default",
            "test",
            (Supplier<List<String>>)
                () ->
                    asList(
                        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                        "    MachineGuid    REG_SZ    test")),
        arguments("short output", null, (Supplier<List<String>>) Collections::emptyList));
  }

  private static void assertHostId(String expectedValue, HostIdResource hostIdResource) {
    MapAssert<AttributeKey<?>, Object> that =
        assertThat(hostIdResource.createResource().getAttributes().asMap());

    if (expectedValue == null) {
      that.isEmpty();
    } else {
      that.containsEntry(HOST_ID, expectedValue);
    }
  }

  @Test
  void shouldApply() {
    HostIdResourceProvider provider = new HostIdResourceProvider();
    assertThat(
            provider.shouldApply(
                DefaultConfigProperties.createFromMap(emptyMap()), Resource.getDefault()))
        .isTrue();
    assertThat(
            provider.shouldApply(
                DefaultConfigProperties.createFromMap(
                    singletonMap("otel.resource.attributes", "host.id=foo")),
                null))
        .isFalse();
  }
}
