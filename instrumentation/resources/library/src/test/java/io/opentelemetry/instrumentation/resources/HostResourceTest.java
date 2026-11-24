/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_ARCH;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junitpioneer.jupiter.SetSystemProperty;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
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
  @SetSystemProperty(key = "os.arch", value = "aarch64")
  void hostArchUsesSemconvValue() {
    Resource resource = HostResource.buildResource();

    assertThat(resource.getAttribute(HOST_ARCH)).isEqualTo("arm64");
  }

  @ParameterizedTest
  @CsvSource({
    "x86_64, amd64",
    "aarch64, arm64",
    "arm, arm32",
    "i386, x86",
    "ppc, ppc32",
    "ppc64le, ppc64",
    "amd64, amd64",
    "s390x, s390x",
    "riscv64, riscv64"
  })
  void toHostArch(String osArch, String hostArch) {
    assertThat(HostResource.toHostArch(osArch)).isEqualTo(hostArch);
  }
}
