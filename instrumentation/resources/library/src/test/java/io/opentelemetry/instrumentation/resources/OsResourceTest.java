/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_VERSION;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.AIX;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.DARWIN;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.DRAGONFLYBSD;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.FREEBSD;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.HPUX;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.LINUX;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.NETBSD;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.OPENBSD;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.SOLARIS;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.WINDOWS;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.ZOS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;

class OsResourceTest {

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.11")
  @SetSystemProperty(key = "os.version", value = "5.10")
  void linux() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OS_TYPE)).isEqualTo(LINUX);
    assertThat(resource.getAttribute(OS_DESCRIPTION)).isNotEmpty();
    assertThat(resource.getAttribute(OS_VERSION)).isEqualTo("5.10");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("osTypeTestCases")
  void osType(String name, String osName, String expectedOsType) {
    String originalOsName = System.getProperty("os.name");
    try {
      System.setProperty("os.name", osName);

      Resource resource = OsResource.buildResource();
      assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
      assertThat(resource.getAttribute(OS_TYPE)).isEqualTo(expectedOsType);
      assertThat(resource.getAttribute(OS_DESCRIPTION)).isNotEmpty();
    } finally {
      restoreSystemProperty("os.name", originalOsName);
    }
  }

  private static Stream<Arguments> osTypeTestCases() {
    return Stream.of(
        Arguments.of("macos", "MacOS X 11", DARWIN),
        Arguments.of("windows", "Windows 10", WINDOWS),
        Arguments.of("freebsd", "FreeBSD 10", FREEBSD),
        Arguments.of("netbsd", "NetBSD 10", NETBSD),
        Arguments.of("openbsd", "OpenBSD 10", OPENBSD),
        Arguments.of("dragonflybsd", "DragonFlyBSD 10", DRAGONFLYBSD),
        Arguments.of("hpux", "HP-UX 10", HPUX),
        Arguments.of("aix", "AIX 10", AIX),
        Arguments.of("solaris", "Solaris 10", SOLARIS),
        Arguments.of("zos", "Z/OS 10", ZOS),
        Arguments.of("unknown", "RagOS 10", null));
  }

  private static void restoreSystemProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
