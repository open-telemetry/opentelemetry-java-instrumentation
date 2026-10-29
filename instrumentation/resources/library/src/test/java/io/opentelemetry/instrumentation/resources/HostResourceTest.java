/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ARCH;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ID;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
import io.opentelemetry.semconv.SchemaUrls;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class HostResourceTest {
  @Test
  void shouldCreateRuntimeAttributes() {
    // when
    Resource resource = HostResource.buildResource();
    Attributes attributes = resource.getAttributes();

    // then
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_40_0);
    assertThat(attributes.get(HOST_NAME)).isNotBlank();
    assertThat(attributes.get(HOST_ARCH)).isNotBlank();
  }

  @Test
  void hostIdDisabled() {
    HostResource host = spy(new HostResource(null, null, null));

    assertThat(host.createResource(false)).isEqualTo(HostResource.get());
    assertThat(HostResource.create(false)).isEqualTo(HostResource.get());
    verify(host, never()).detectHostId();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "host-id")
  void hostIdEnabled(String hostId) {
    HostResource host =
        new HostResource(
            () -> "linux", path -> hostId == null ? emptyList() : singletonList(hostId), null);

    Resource resource = host.createResource(true);

    assertThat(resource)
        .isEqualTo(
            Resource.create(
                HostResource.get().getAttributes().toBuilder().put(HOST_ID, hostId).build(),
                SchemaUrls.V1_24_0));
    assertThat(EntityUtil.getEntities(resource)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("linuxCases")
  void linux(String expectedValue, Function<Path, List<String>> fileReader) {
    HostResource host = new HostResource(() -> "linux", fileReader, null);
    assertThat(host.detectHostId()).isEqualTo(expectedValue);
  }

  private static Stream<Arguments> linuxCases() {
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
  @MethodSource("windowsCases")
  void windows(String expectedValue, Function<List<String>, List<String>> command) {
    HostResource host = new HostResource(() -> "Windows 95", null, command);
    assertThat(host.detectHostId()).isEqualTo(expectedValue);
  }

  private static Stream<Arguments> windowsCases() {
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
    assertThat(HostResource.windowsRegPath(getEnv)).isEqualTo(expectedPath);
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
  @MethodSource("macOsCases")
  void macOs(String expectedValue, Function<List<String>, List<String>> command) {
    HostResource host = new HostResource(() -> "Mac OS X", null, command);
    assertThat(host.detectHostId()).isEqualTo(expectedValue);
  }

  private static Stream<Arguments> macOsCases() {
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
  @MethodSource("bsdCases")
  void bsd(
      String expectedValue,
      Function<Path, List<String>> fileReader,
      Function<List<String>, List<String>> command) {
    HostResource host = new HostResource(() -> "FreeBSD", fileReader, command);
    assertThat(host.detectHostId()).isEqualTo(expectedValue);
  }

  private static Stream<Arguments> bsdCases() {
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

  @Test
  void unsupportedOs() {
    assertThat(new HostResource(() -> "unsupported", null, null).detectHostId()).isNull();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandReturnsOutput() {
    assertThat(HostResource.runCommand(asList("/bin/sh", "-c", "echo hello"), 5000))
        .containsExactly("hello");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandTimesOut() {
    long startNanos = System.nanoTime();
    assertThat(HostResource.runCommand(asList("/bin/sh", "-c", "sleep 30"), 200)).isEmpty();
    // the command would take 30s to complete, so anything close to that means the timeout was
    // not applied
    assertThat(NANOSECONDS.toMillis(System.nanoTime() - startNanos)).isLessThan(10_000);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandNonZeroExitCode() {
    assertThat(HostResource.runCommand(asList("/bin/sh", "-c", "echo out; exit 3"), 5000))
        .isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void runCommandLargeOutputDoesNotDeadlock() {
    // more output than fits in the pipe buffer: waiting for the process to exit before reading
    // its output would deadlock here
    assertThat(HostResource.runCommand(asList("/bin/sh", "-c", "seq 1 50000"), 30_000))
        .hasSize(50_000);
  }

  @Test
  void runCommandThatDoesNotExist() {
    assertThat(HostResource.runCommand(singletonList("/nonexistent/otel-test-command"), 5000))
        .isEmpty();
  }
}
