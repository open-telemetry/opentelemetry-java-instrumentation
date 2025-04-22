/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.resources.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContainerResourceTest {

  public static final String TEST_CONTAINER_ID = "abcdef123123deadbeef";
  @Mock CgroupV1ContainerIdExtractor v1;
  @Mock CgroupV2ContainerIdExtractor v2;

  @Test
  void v1Success() {
    when(v1.extractContainerId()).thenReturn(Optional.of(TEST_CONTAINER_ID));
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource.getAttribute(CONTAINER_ID)).isEqualTo(TEST_CONTAINER_ID);
    verifyNoInteractions(v2);
  }

  @Test
  void v2Success() {
    when(v1.extractContainerId()).thenReturn(Optional.empty());
    when(v2.extractContainerId()).thenReturn(Optional.of(TEST_CONTAINER_ID));
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource.getAttribute(CONTAINER_ID)).isEqualTo(TEST_CONTAINER_ID);
  }

  @Test
  void bothVersionsFail() {
    when(v1.extractContainerId()).thenReturn(Optional.empty());
    when(v2.extractContainerId()).thenReturn(Optional.empty());
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource).isSameAs(Resource.empty());
  }

  @Test
  void testAlternateEncoding() throws Exception {
    String containerId = "ac679f8a8319c8cf7d38e1adf263bc08d231f2ff81abda3915f6e8ba4d64156a";
    String line = "13:name=systemd:/podruntime/docker/kubepods/" + containerId + ".aaaa";
    Charset ibmCharset = Charset.forName("Cp1047");
    byte[] utf8 = line.getBytes(StandardCharsets.UTF_8);
    byte[] ibm = line.getBytes(ibmCharset);
    assertThat(ibm).isNotEqualTo(utf8);

    String ibmAsString = new String(ibm, ibmCharset);
    // Different bytes, different encoding, same semantic string value
    assertThat(line).isEqualTo(ibmAsString);

    // Make temp file that contains the IBM encoding
    File tempFile = File.createTempFile("tmp", "mountinfo");
    tempFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(tempFile)) {
      out.write(ibm);
    }
    ContainerResource.Filesystem fs =
        // pretend we are on z/OS to trigger the routine to detect an alternative encoding
        new ContainerResource.Filesystem(() -> "z/OS");
    CgroupV1ContainerIdExtractor extractor =
        new CgroupV1ContainerIdExtractor(fs, tempFile.toPath());
    ContainerResource testClass = new ContainerResource(extractor, null);

    Resource resource = testClass.buildResource();
    assertThat(resource.getAttribute(CONTAINER_ID)).isEqualTo(containerId);
  }
}
