/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.CgroupV2ContainerIdExtractor.V2_CGROUP_PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

  private void verifyContainerId(String rawFileContent, String containerId) throws Exception {
    when(filesystem.isReadable(V2_CGROUP_PATH)).thenReturn(true);
    when(filesystem.lineList(V2_CGROUP_PATH)).thenReturn(fileToListOfLines(rawFileContent));
    CgroupV2ContainerIdExtractor extractor = new CgroupV2ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result.orElse("fail")).isEqualTo(containerId);
  }

  @Test
  void extractSuccess_docker() throws Exception {
    verifyContainerId(
        "docker_proc_self_mountinfo",
        "be522444b60caf2d3934b8b24b916a8a314f4b68d4595aa419874657e8d103f2");
  }

  @Test
  void extractSuccess_docker1() throws Exception {
    verifyContainerId(
        "docker_proc_self_mountinfo1",
        "188329f95b930c32eeeffd34658ed2538960947e166743fa3743f5ce3d739b40");
  }

  @Test
  void extractSuccess_containerd() throws Exception {
    verifyContainerId(
        "containerd_proc_self_mountinfo",
        "f2a44bc8e090f93a2b4d7f510bdaff0615ad52906e3287ee956dcf5aa5012a91");
  }

  @Test
  void extractSuccess_podman() throws Exception {
    verifyContainerId(
        "podman_proc_self_mountinfo",
        "2a33efc76e519c137fe6093179653788bed6162d4a15e5131c8e835c968afbe6");
  }

  @Test
  void extractSuccess_crio() throws Exception {
    verifyContainerId(
        "crio_proc_self_mountinfo",
        "a8f62e52ed7c2cd85242dcf0eb1d727b643540ceca7f328ad7d2f31aedf07731");
  }

  @Test
  void extractSuccess_crio1() throws Exception {
    verifyContainerId(
        "crio_proc_self_mountinfo1",
        "f23ec1d4b715c6531a17e9c549222fbbe1f7ffff697a29a2212b3b4cdc37f52e");
  }

  @Test
  void extractSuccess_crio2() throws Exception {
    verifyContainerId(
        "crio_proc_self_mountinfo2",
        "b4873629b312dc1d77472aba6fb177c6ce9a8f7c205ad7a03302726805007fe6");
  }

  private static List<String> fileToListOfLines(String filename) {
    InputStream in =
        CgroupV2ContainerIdExtractorTest.class.getClassLoader().getResourceAsStream(filename);
    return new BufferedReader(new InputStreamReader(in, UTF_8))
        .lines()
        .collect(Collectors.toList());
  }
}
