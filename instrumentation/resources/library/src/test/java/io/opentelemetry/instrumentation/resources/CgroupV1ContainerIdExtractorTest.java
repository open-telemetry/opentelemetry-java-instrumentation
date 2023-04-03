/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.CgroupV1ContainerIdExtractor.V1_CGROUP_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CgroupV1ContainerIdExtractorTest {

  @Mock ContainerResource.Filesystem filesystem;

  @Test
  void fileNotReadable() throws IOException {
    when(filesystem.isReadable(V1_CGROUP_PATH)).thenReturn(false);
    CgroupV1ContainerIdExtractor extractor = new CgroupV1ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result).isEmpty();
    verify(filesystem, never()).lines(any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // invalid containerId (non-hex)
        "13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d23zzzz",
        // unrecognized format (last "-" is after last ".")
        "13:name=systemd:/podruntime/docker/kubepods/ac679f8.a8319c8cf7d38e1adf263bc08-d23zzzz"
      })
  void invalidContainerIds(String line) throws IOException {
    when(filesystem.isReadable(V1_CGROUP_PATH)).thenReturn(true);
    when(filesystem.lines(V1_CGROUP_PATH)).thenReturn(Stream.of(line));

    CgroupV1ContainerIdExtractor extractor = new CgroupV1ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("validLines")
  void validCgroupLines(String line, String expectedContainerId) throws IOException {
    when(filesystem.isReadable(V1_CGROUP_PATH)).thenReturn(true);
    when(filesystem.lines(V1_CGROUP_PATH)).thenReturn(Stream.of(line));

    CgroupV1ContainerIdExtractor extractor = new CgroupV1ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result.orElse("fail")).isEqualTo(expectedContainerId);
  }

  // See https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7437
  @Test
  void hostStyleCgroupFile() throws IOException {
    String line = "1:name=systemd:/user.slice/user-0.slice/session-31207.scope";
    line = "1:name=systemd:/user.slice/user-1000.slice/session-4.scope";
    when(filesystem.isReadable(V1_CGROUP_PATH)).thenReturn(true);
    when(filesystem.lines(V1_CGROUP_PATH)).thenReturn(Stream.of(line));

    CgroupV1ContainerIdExtractor extractor = new CgroupV1ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result).isEmpty();
  }

  static Stream<Arguments> validLines() {
    return Stream.of(
        // with suffix
        arguments(
            "13:name=systemd:/podruntime/docker/kubepods/ac679f8a8319c8cf7d38e1adf263bc08d231f2ff81abda3915f6e8ba4d64156a.aaaa",
            "ac679f8a8319c8cf7d38e1adf263bc08d231f2ff81abda3915f6e8ba4d64156a"),
        // with prefix and suffix
        arguments(
            "13:name=systemd:/podruntime/docker/kubepods/crio-dc679f8a8319c8cf7d38e1adf263bc08d234f0749ea715fb6ca3bb259db69956.stuff",
            "dc679f8a8319c8cf7d38e1adf263bc08d234f0749ea715fb6ca3bb259db69956"),
        // just container id
        arguments(
            "13:name=systemd:/pod/d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356",
            "d86d75589bf6cc254f3e2cc29debdf85dde404998aa128997a819ff991827356"),
        // with prefix
        arguments(
            "//\n"
                + "1:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d230600179b07acfd7eaf9646778dc31"
                + "2:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d230600179b07acfd7eaf9646778dc31"
                + "3:name=systemd:/podruntime/docker/kubepods/docker-dc579f8a8319c8cf7d38e1adf263bc08d230600179b07acfd7eaf9646778dc31",
            "dc579f8a8319c8cf7d38e1adf263bc08d230600179b07acfd7eaf9646778dc31"),
        // with two dashes in prefix
        arguments(
            "11:perf_event:/kubepods.slice/kubepods-burstable.slice/kubepods-burstable-pod4415fd05_2c0f_4533_909b_f2180dca8d7c.slice/cri-containerd-713a77a26fe2a38ebebd5709604a048c3d380db1eb16aa43aca0b2499e54733c.scope",
            "713a77a26fe2a38ebebd5709604a048c3d380db1eb16aa43aca0b2499e54733c"),
        // with colon, env: k8s v1.24.0, the cgroupDriver by systemd(default), and container is
        // cri-containerd v1.6.8
        arguments(
            "11:devices:/system.slice/containerd.service/kubepods-pod87a18a64_b74a_454a_b10b_a4a36059d0a3.slice:cri-containerd:05c48c82caff3be3d7f1e896981dd410e81487538936914f32b624d168de9db0",
            "05c48c82caff3be3d7f1e896981dd410e81487538936914f32b624d168de9db0"));
  }
}
