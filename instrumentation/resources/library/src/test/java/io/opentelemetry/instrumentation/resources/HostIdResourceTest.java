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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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

  @ParameterizedTest
  @MethodSource("createResourceLinuxCases")
  void createResourceLinux(String expectedValue, Function<Path, List<String>> fileReader) {
    HostIdResource hostIdResource = new HostIdResource(() -> "linux", fileReader, null, null);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceLinuxCases() {
    return Stream.of(
        argumentSet(
            "default", "test", (Function<Path, List<String>>) path -> singletonList("test")),
        argumentSet(
            "dbus fallback",
            "dbus-id",
            (Function<Path, List<String>>)
                path ->
                    path.endsWith("machine-id") && path.toString().contains("dbus")
                        ? singletonList("dbus-id")
                        : emptyList()),
        argumentSet(
            "empty file or error reading",
            null,
            (Function<Path, List<String>>) path -> emptyList()));
  }

  @ParameterizedTest
  @MethodSource("createResourceWindowsCases")
  void createResourceWindows(String expectedValue, Supplier<List<String>> queryWindowsRegistry) {
    HostIdResource hostIdResource =
        new HostIdResource(() -> "Windows 95", null, queryWindowsRegistry, null);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceWindowsCases() {
    return Stream.of(
        argumentSet(
            "default",
            "test",
            (Supplier<List<String>>)
                () ->
                    asList(
                        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                        "    MachineGuid    REG_SZ    test")),
        argumentSet("short output", null, (Supplier<List<String>>) Collections::emptyList));
  }

  @ParameterizedTest
  @MethodSource("createResourceMacOsCases")
  void createResourceMacOs(String expectedValue, Function<List<String>, List<String>> command) {
    HostIdResource hostIdResource = new HostIdResource(() -> "Mac OS X", null, null, command);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceMacOsCases() {
    return Stream.of(
        argumentSet(
            "default",
            "0123456789ABCDEF",
            (Function<List<String>, List<String>>)
                command ->
                    asList(
                        "+-o IOPlatformExpertDevice  <class IOPlatformExpertDevice>",
                        "    \"IOPlatformUUID\" = \"0123456789ABCDEF\"")),
        argumentSet(
            "no uuid",
            null,
            (Function<List<String>, List<String>>)
                command -> singletonList("+-o IOPlatformExpertDevice")),
        argumentSet(
            "empty output", null, (Function<List<String>, List<String>>) command -> emptyList()));
  }

  @ParameterizedTest
  @MethodSource("createResourceBsdCases")
  void createResourceBsd(
      String expectedValue,
      Function<Path, List<String>> fileReader,
      Function<List<String>, List<String>> command) {
    HostIdResource hostIdResource = new HostIdResource(() -> "FreeBSD", fileReader, null, command);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceBsdCases() {
    return Stream.of(
        argumentSet(
            "hostid file",
            "hostid-value",
            (Function<Path, List<String>>) path -> singletonList("hostid-value"),
            (Function<List<String>, List<String>>) command -> emptyList()),
        argumentSet(
            "kenv fallback",
            "kenv-uuid",
            (Function<Path, List<String>>) path -> emptyList(),
            (Function<List<String>, List<String>>) command -> singletonList("kenv-uuid")),
        argumentSet(
            "nothing found",
            null,
            (Function<Path, List<String>>) path -> emptyList(),
            (Function<List<String>, List<String>>) command -> emptyList()));
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
