/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ID;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class HostIdResourceTest {

  private static class LinuxTestCase {
    private final String name;
    private final String expectedValue;
    private final Function<Path, List<String>> pathReader;

    private LinuxTestCase(
        String name, String expectedValue, Function<Path, List<String>> pathReader) {
      this.name = name;
      this.expectedValue = expectedValue;
      this.pathReader = pathReader;
    }
  }

  private static class WindowsTestCase {
    private final String name;
    private final String expectedValue;
    private final Supplier<List<String>> queryWindowsRegistry;

    private WindowsTestCase(
        String name, String expectedValue, Supplier<List<String>> queryWindowsRegistry) {
      this.name = name;
      this.expectedValue = expectedValue;
      this.queryWindowsRegistry = queryWindowsRegistry;
    }
  }

  @TestFactory
  Collection<DynamicTest> createResourceLinux() {
    return Stream.of(
            new LinuxTestCase("default", "test", path -> singletonList("test")),
            new LinuxTestCase("empty file or error reading", null, path -> emptyList()))
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.name,
                    () -> {
                      HostIdResource hostIdResource =
                          new HostIdResource(() -> "linux", testCase.pathReader, null);

                      assertHostId(testCase.expectedValue, hostIdResource);
                    }))
        .collect(toList());
  }

  @TestFactory
  Collection<DynamicTest> createResourceWindows() {
    return Stream.of(
            new WindowsTestCase(
                "default",
                "test",
                () ->
                    Arrays.asList(
                        "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography",
                        "    MachineGuid    REG_SZ    test")),
            new WindowsTestCase("short output", null, Collections::emptyList))
        .map(
            testCase ->
                DynamicTest.dynamicTest(
                    testCase.name,
                    () -> {
                      HostIdResource hostIdResource =
                          new HostIdResource(
                              () -> "Windows 95", null, testCase.queryWindowsRegistry);

                      assertHostId(testCase.expectedValue, hostIdResource);
                    }))
        .collect(toList());
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
