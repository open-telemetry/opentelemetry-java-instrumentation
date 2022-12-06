/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.CgroupV2ContainerIdExtractor.V2_CGROUP_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CgroupV2ContainerIdExtractorTest {

  @Mock ContainerResource.Filesystem filesystem;

  @Test
  void fileNotReadable() {
    when(filesystem.isReadable(V2_CGROUP_PATH)).thenReturn(false);
    CgroupV2ContainerIdExtractor extractor = new CgroupV2ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result).isSameAs(Optional.empty());
  }

  @Test
  void extractSuccess_docker() throws Exception {
    when(filesystem.isReadable(V2_CGROUP_PATH)).thenReturn(true);
    Stream<String> fileContent = getTestDockerFileContent();
    when(filesystem.lines(V2_CGROUP_PATH)).thenReturn(fileContent);
    CgroupV2ContainerIdExtractor extractor = new CgroupV2ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result.orElse("fail"))
        .isEqualTo("be522444b60caf2d3934b8b24b916a8a314f4b68d4595aa419874657e8d103f2");
  }

  @Test
  void extractSuccess_podman() throws Exception {
    when(filesystem.isReadable(V2_CGROUP_PATH)).thenReturn(true);
    Stream<String> fileContent = getTestPodmanFileContent();
    when(filesystem.lines(V2_CGROUP_PATH)).thenReturn(fileContent);
    CgroupV2ContainerIdExtractor extractor = new CgroupV2ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result.orElse("fail"))
        .isEqualTo("2a33efc76e519c137fe6093179653788bed6162d4a15e5131c8e835c968afbe6");
  }

  private static Stream<String> getTestDockerFileContent() throws Exception {
    return fileToStreamOfLines("docker_proc_self_mountinfo");
  }

  private static Stream<String> getTestPodmanFileContent() throws Exception {
    return fileToStreamOfLines("podman_proc_self_mountinfo");
  }

  private static Stream<String> fileToStreamOfLines(String filename) throws IOException {
    try (InputStream in =
        CgroupV2ContainerIdExtractorTest.class.getClassLoader().getResourceAsStream(filename)) {
      byte[] buff = new byte[100 * 1024];
      int rc = in.read(buff);
      String file = new String(buff, 0, rc, StandardCharsets.UTF_8);
      return Stream.of(file.split("\n"));
    }
  }
}
