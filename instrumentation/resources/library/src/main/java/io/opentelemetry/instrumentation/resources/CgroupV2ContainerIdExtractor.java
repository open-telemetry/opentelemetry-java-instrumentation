/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.Optional.empty;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Utility for extracting the container ID from runtimes inside cgroup v2 containers. */
class CgroupV2ContainerIdExtractor {

  private static final Logger logger =
      Logger.getLogger(CgroupV2ContainerIdExtractor.class.getName());

  static final Path V2_CGROUP_PATH = Paths.get("/proc/self/mountinfo");
  private static final Pattern CONTAINER_ID_RE = Pattern.compile("^[0-9a-f]{64}$");
  private static final Pattern CONTAINERD_CONTAINER_ID_RE =
      Pattern.compile("cri-containerd:[0-9a-f]{64}");
  private static final Pattern CRIO_CONTAINER_ID_RE = Pattern.compile("\\/crio-[0-9a-f]{64}");

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

    List<String> fileAsList;
    try {
      fileAsList = filesystem.lineList(V2_CGROUP_PATH);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Unable to read v2 cgroup path", e);
      return empty();
    }

    Optional<String> optCid =
        fileAsList.stream()
            .filter(line -> line.contains("/crio-"))
            .map(CRIO_CONTAINER_ID_RE::matcher)
            .filter(Matcher::find)
            .findFirst()
            .map(matcher -> matcher.group(0).substring(6));
    if (optCid.isPresent()) {
      return optCid;
    }

    optCid =
        fileAsList.stream()
            .filter(line -> line.contains("cri-containerd:"))
            .map(CONTAINERD_CONTAINER_ID_RE::matcher)
            .filter(Matcher::find)
            .findFirst()
            .map(matcher -> matcher.group(0).substring(15));
    if (optCid.isPresent()) {
      return optCid;
    }

    return fileAsList.stream()
        .filter(line -> line.contains("/containers/"))
        .flatMap(line -> Stream.of(line.split("/")))
        .map(CONTAINER_ID_RE::matcher)
        .filter(Matcher::matches)
        .reduce((first, second) -> second)
        .map(matcher -> matcher.group(0));
  }
}
