package io.opentelemetry.instrumentation.resources;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/** Utility for extracting the container ID from runtimes inside cgroup v2 containers.*/
class CGroupsV2ContainerIdExtractor {

  static final Path V2_CGROUP_FILE_PATH = Paths.get("/proc/self/mountinfo");

  CGroupsV2ContainerIdExtractor(){
    this(ContainerResource.FILESYSTEM_INSTANCE);
  }
  private final ContainerResource.Filesystem filesystem;

  // Exists for testing
  CGroupsV2ContainerIdExtractor(
      ContainerResource.Filesystem filesystem) {this.filesystem = filesystem;}

  Optional<String> extractContainerId() {
    return Optional.empty();
  }
}
