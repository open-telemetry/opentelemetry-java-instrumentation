/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.instrumentation.resources.CgroupV2ContainerIdExtractor.V2_CGROUP_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
  void extractSuccess() throws Exception {
    when(filesystem.isReadable(V2_CGROUP_PATH)).thenReturn(true);
    Stream<String> fileContent = getTestFileContent();
    when(filesystem.lines(V2_CGROUP_PATH)).thenReturn(fileContent);
    CgroupV2ContainerIdExtractor extractor = new CgroupV2ContainerIdExtractor(filesystem);
    Optional<String> result = extractor.extractContainerId();
    assertThat(result.orElse("fail"))
        .isEqualTo("dc64b5743252dbaef6e30521c34d6bbd1620c8ce65bdb7bf9e7143b61bb5b183");
  }

  private static Stream<String> getTestFileContent() {
    return Stream.of(
        "456 375 0:143 / / rw,relatime master:175 - overlay overlay rw,lowerdir=/var/lib/docker/overlay2/l/37L57D2IM7MEWLVE2Q2ECNDT67:/var/lib/docker/overlay2/l/46FCA2JFPCSNFGAR5TSYLLNHLK,upperdir=/var/lib/docker/overlay2/4e82c300793d703c19bdf887bfdad8b0354edda884ea27a8a2df89ab292719a4/diff,workdir=/var/lib/docker/overlay2/4e82c300793d703c19bdf887bfdad8b0354edda884ea27a8a2df89ab292719a4/work",
        "457 456 0:146 / /proc rw,nosuid,nodev,noexec,relatime - proc proc rw",
        "466 456 0:147 / /dev rw,nosuid - tmpfs tmpfs rw,size=65536k,mode=755",
        "467 466 0:148 / /dev/pts rw,nosuid,noexec,relatime - devpts devpts rw,gid=5,mode=620,ptmxmode=666",
        "468 456 0:149 / /sys ro,nosuid,nodev,noexec,relatime - sysfs sysfs ro",
        "469 468 0:30 / /sys/fs/cgroup ro,nosuid,nodev,noexec,relatime - cgroup2 cgroup rw",
        "470 466 0:145 / /dev/mqueue rw,nosuid,nodev,noexec,relatime - mqueue mqueue rw",
        "471 466 0:150 / /dev/shm rw,nosuid,nodev,noexec,relatime - tmpfs shm rw,size=65536k",
        "472 456 254:1 /docker/containers/dc64b5743252dbaef6e30521c34d6bbd1620c8ce65bdb7bf9e7143b61bb5b183/resolv.conf /etc/resolv.conf rw,relatime - ext4 /dev/vda1 rw",
        "473 456 254:1 /docker/containers/dc64b5743252dbaef6e30521c34d6bbd1620c8ce65bdb7bf9e7143b61bb5b183/hostname /etc/hostname rw,relatime - ext4 /dev/vda1 rw",
        "474 456 254:1 /docker/containers/dc64b5743252dbaef6e30521c34d6bbd1620c8ce65bdb7bf9e7143b61bb5b183/hosts /etc/hosts rw,relatime - ext4 /dev/vda1 rw",
        "376 466 0:148 /0 /dev/console rw,nosuid,noexec,relatime - devpts devpts rw,gid=5,mode=620,ptmxmode=666",
        "377 457 0:146 /bus /proc/bus ro,nosuid,nodev,noexec,relatime - proc proc rw",
        "378 457 0:146 /fs /proc/fs ro,nosuid,nodev,noexec,relatime - proc proc rw",
        "379 457 0:146 /irq /proc/irq ro,nosuid,nodev,noexec,relatime - proc proc rw",
        "380 457 0:146 /sys /proc/sys ro,nosuid,nodev,noexec,relatime - proc proc rw",
        "381 457 0:146 /sysrq-trigger /proc/sysrq-trigger ro,nosuid,nodev,noexec,relatime - proc proc rw",
        "382 457 0:151 / /proc/acpi ro,relatime - tmpfs tmpfs ro",
        "383 457 0:147 /null /proc/kcore rw,nosuid - tmpfs tmpfs rw,size=65536k,mode=755",
        "384 457 0:147 /null /proc/keys rw,nosuid - tmpfs tmpfs rw,size=65536k,mode=755",
        "385 457 0:147 /null /proc/timer_list rw,nosuid - tmpfs tmpfs rw,size=65536k,mode=755",
        "386 468 0:152 / /sys/firmware ro,relatime - tmpfs tmpfs ");
  }
}
