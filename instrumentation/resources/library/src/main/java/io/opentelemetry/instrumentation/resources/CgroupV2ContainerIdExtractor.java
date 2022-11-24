/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.Optional.empty;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility for extracting the container ID from runtimes inside cgroup v2 containers. */
class CgroupV2ContainerIdExtractor {

  private static final Logger logger =
      Logger.getLogger(CgroupV2ContainerIdExtractor.class.getName());

  static final Path V2_CGROUP_PATH = Paths.get("/proc/self/mountinfo");
  private static final Pattern CONTAINER_RE =
      Pattern.compile(".*/docker/containers/([0-9a-f]{64})/.*");

  private final ContainerResource.Filesystem filesystem;

  CgroupV2ContainerIdExtractor() {
    this(ContainerResource.FILESYSTEM_INSTANCE);
  }

  // Exists for testing
  CgroupV2ContainerIdExtractor(ContainerResource.Filesystem filesystem) {
    this.filesystem = filesystem;
  }

  Optional<String> extractContainerId() {
    if (!filesystem.isReadable(V2_CGROUP_PATH)) {
      return empty();
    }
    try {
      return filesystem
          .lines(V2_CGROUP_PATH)
          .map(CONTAINER_RE::matcher)
          .filter(Matcher::matches)
          .findFirst()
          .map(matcher -> matcher.group(1));
    } catch (IOException e) {
      logger.log(Level.WARNING, "Unable to read v2 cgroup path", e);
    }
    return empty();
  }
}
