/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.internal.OtelEncodingUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Utility for extracting the container ID from runtimes inside cgroup v1 containers.*/
final class CGroupsV1ContainerIdExtractor {

  private static final Logger logger = Logger.getLogger(CGroupsV1ContainerIdExtractor.class.getName());
  static final Path V1_CGROUP_FILE_PATH = Paths.get("/proc/self/cgroup");
  private final ContainerResource.Filesystem filesystem;

  CGroupsV1ContainerIdExtractor(){
    this(ContainerResource.FILESYSTEM_INSTANCE);
  }

  //Exists for testing
  CGroupsV1ContainerIdExtractor(ContainerResource.Filesystem filesystem) {
    this.filesystem = filesystem;
  }


  /**
   * Each line of cgroup file looks like "14:name=systemd:/docker/.../... A hex string is expected
   * inside the last section separated by '/' Each segment of the '/' can contain metadata separated
   * by either '.' (at beginning) or '-' (at end)
   *
   * @return containerId
   */
  Optional<String> extractContainerId() {
    if (!filesystem.isReadable(V1_CGROUP_FILE_PATH)) {
      return Optional.empty();
    }
    try (Stream<String> lines = filesystem.lines(V1_CGROUP_FILE_PATH)) {
      return lines
          .filter(line -> !line.isEmpty())
          .map(CGroupsV1ContainerIdExtractor::getIdFromLine)
          .filter(Optional::isPresent)
          .findFirst()
          .orElse(Optional.empty());
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to read file", e);
    }
    return Optional.empty();
  }

  private static Optional<String> getIdFromLine(String line) {
    // This cgroup output line should have the container id in it
    int lastSlashIdx = line.lastIndexOf('/');
    if (lastSlashIdx < 0) {
      return Optional.empty();
    }

    String containerId;

    String lastSection = line.substring(lastSlashIdx + 1);
    int colonIdx = lastSection.lastIndexOf(':');

    if (colonIdx != -1) {
      // since containerd v1.5.0+, containerId is divided by the last colon when the cgroupDriver is
      // systemd:
      // https://github.com/containerd/containerd/blob/release/1.5/pkg/cri/server/helpers_linux.go#L64
      containerId = lastSection.substring(colonIdx + 1);
    } else {
      int startIdx = lastSection.lastIndexOf('-');
      int endIdx = lastSection.lastIndexOf('.');

      startIdx = startIdx == -1 ? 0 : startIdx + 1;
      if (endIdx == -1) {
        endIdx = lastSection.length();
      }
      if (startIdx > endIdx) {
        return Optional.empty();
      }

      containerId = lastSection.substring(startIdx, endIdx);
    }

    if (OtelEncodingUtils.isValidBase16String(containerId) && !containerId.isEmpty()) {
      return Optional.of(containerId);
    } else {
      return Optional.empty();
    }
  }
}
