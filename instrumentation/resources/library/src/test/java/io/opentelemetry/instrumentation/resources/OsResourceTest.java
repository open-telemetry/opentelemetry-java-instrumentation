/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

class OsResourceTest {

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.11")
  void linux() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.LINUX);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "MacOS X 11")
  void macos() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.DARWIN);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Windows 10")
  void windows() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.WINDOWS);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "FreeBSD 10")
  void freebsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.FREEBSD);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "NetBSD 10")
  void netbsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.NETBSD);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "OpenBSD 10")
  void openbsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.OPENBSD);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "DragonFlyBSD 10")
  void dragonflybsd() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.DRAGONFLYBSD);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "HP-UX 10")
  void hpux() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.HPUX);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "AIX 10")
  void aix() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.AIX);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Solaris 10")
  void solaris() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.SOLARIS);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Z/OS 10")
  void zos() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE))
        .isEqualTo(OsIncubatingAttributes.OsTypeIncubatingValues.Z_OS);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "RagOS 10")
  void unknown() {
    Resource resource = OsResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_TYPE)).isNull();
    assertThat(resource.getAttribute(OsIncubatingAttributes.OS_DESCRIPTION)).isNotEmpty();
  }
}
