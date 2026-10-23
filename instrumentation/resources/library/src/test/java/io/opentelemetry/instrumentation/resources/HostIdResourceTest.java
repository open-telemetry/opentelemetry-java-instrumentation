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
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HostIdResourceTest {

  @ParameterizedTest
  @MethodSource("createResourceLinuxCases")
  void createResourceLinux(String expectedValue, Function<Path, List<String>> fileReader) {
    HostIdResource hostIdResource = new HostIdResource(() -> "linux", fileReader, null);
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
  void createResourceWindows(String expectedValue, Function<List<String>, List<String>> command) {
    HostIdResource hostIdResource = new HostIdResource(() -> "Windows 95", null, command);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceWindowsCases() {
    return Stream.of(
        argumentSet(
            "default",
            "test",
            (Function<List<String>, List<String>>)
                command -> {
                  assertThat(command.get(0)).endsWith("\\System32\\reg.exe");
                  assertThat(command.subList(1, command.size()))
                      .containsExactly(
                          "query",
                          "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                          "/v",
                          "MachineGuid");
                  return asList(
                      "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                      "    MachineGuid    REG_SZ    test");
                }),
        argumentSet(
            "short output", null, (Function<List<String>, List<String>>) command -> emptyList()));
  }

  @ParameterizedTest
  @MethodSource("windowsRegPathCases")
  void windowsRegPath(String expectedPath, Function<String, String> getEnv) {
    assertThat(HostIdResource.windowsRegPath(getEnv)).isEqualTo(expectedPath);
  }

  private static Stream<Arguments> windowsRegPathCases() {
    return Stream.of(
        argumentSet(
            "SystemRoot",
            "D:\\Win\\System32\\reg.exe",
            (Function<String, String>) name -> "SystemRoot".equals(name) ? "D:\\Win" : null),
        argumentSet(
            "windir fallback",
            "E:\\Win\\System32\\reg.exe",
            (Function<String, String>) name -> "windir".equals(name) ? "E:\\Win" : null),
        argumentSet(
            "neither set",
            "C:\\Windows\\System32\\reg.exe",
            (Function<String, String>) name -> null),
        argumentSet(
            "empty values",
            "C:\\Windows\\System32\\reg.exe",
            (Function<String, String>) name -> ""));
  }

  @ParameterizedTest
  @MethodSource("createResourceMacOsCases")
  void createResourceMacOs(String expectedValue, Function<List<String>, List<String>> command) {
    HostIdResource hostIdResource = new HostIdResource(() -> "Mac OS X", null, command);
    assertHostId(expectedValue, hostIdResource);
  }

  private static Stream<Arguments> createResourceMacOsCases() {
    return Stream.of(
        argumentSet(
            "default",
            "0123456789ABCDEF",
            (Function<List<String>, List<String>>)
                command -> {
                  assertThat(command)
                      .containsExactly("/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
                  return asList(
                      "+-o IOPlatformExpertDevice  <class IOPlatformExpertDevice>",
                      "    \"IOPlatformUUID\" = \"0123456789ABCDEF\"");
                }),
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
    HostIdResource hostIdResource = new HostIdResource(() -> "FreeBSD", fileReader, command);
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
            (Function<List<String>, List<String>>)
                command -> {
                  assertThat(command).containsExactly("/bin/kenv", "-q", "smbios.system.uuid");
                  return singletonList("kenv-uuid");
                }),
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

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandReturnsOutput() {
    assertThat(HostIdResource.runCommand(asList("/bin/sh", "-c", "echo hello"), 5000))
        .containsExactly("hello");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandTimesOut() {
    long startNanos = System.nanoTime();
    assertThat(HostIdResource.runCommand(asList("/bin/sh", "-c", "sleep 30"), 200)).isEmpty();
    // the command would take 30s to complete, so anything close to that means the timeout was
    // not applied
    assertThat(NANOSECONDS.toMillis(System.nanoTime() - startNanos)).isLessThan(10_000);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandNonZeroExitCode() {
    assertThat(HostIdResource.runCommand(asList("/bin/sh", "-c", "echo out; exit 3"), 5000))
        .isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandLargeOutputDoesNotDeadlock() {
    // more output than fits in the pipe buffer: waiting for the process to exit before reading
    // its output would deadlock here
    assertThat(HostIdResource.runCommand(asList("/bin/sh", "-c", "seq 1 50000"), 30_000))
        .hasSize(50_000);
  }

  @Test
  void runCommandThatDoesNotExist() {
    assertThat(HostIdResource.runCommand(singletonList("/nonexistent/otel-test-command"), 5000))
        .isEmpty();
  }
}
