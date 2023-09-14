/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.SetSystemProperty;

class OsResourceTest {

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.11")
  void linux() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.LINUX);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "MacOS X 11")
  void macos() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.DARWIN);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Windows 10")
  void windows() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.WINDOWS);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "FreeBSD 10")
  void freebsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.FREEBSD);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "NetBSD 10")
  void netbsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.NETBSD);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "OpenBSD 10")
  void openbsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.OPENBSD);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "DragonFlyBSD 10")
  void dragonflybsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.DRAGONFLYBSD);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "HP-UX 10")
  void hpux() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.HPUX);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "AIX 10")
  void aix() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.AIX);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Solaris 10")
  void solaris() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.SOLARIS);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Z/OS 10")
  void zos() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE))
        .isEqualTo(ResourceAttributes.OsTypeValues.Z_OS);
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "RagOS 10")
  void unknown() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(ResourceAttributes.SCHEMA_URL);
    assertThat(resource.getAttribute(ResourceAttributes.OS_TYPE)).isNull();
    assertThat(resource.getAttribute(ResourceAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @ExtendWith(SecurityManagerExtension.class)
  @EnabledOnJre(
      value = {JRE.JAVA_8, JRE.JAVA_11, JRE.JAVA_16},
      disabledReason = "Java 17 deprecates security manager for removal")
  static class SecurityManagerEnabled {
    @Test
    void empty() {
      assertThat(OsResource.buildResource()).isEqualTo(Resource.empty());
    }
  }
}
