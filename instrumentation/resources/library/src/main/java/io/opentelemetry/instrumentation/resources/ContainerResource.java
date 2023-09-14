/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.ResourceAttributes.CONTAINER_ID;

import com.google.errorprone.annotations.MustBeClosed;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Factory for {@link Resource} retrieving Container ID information. It supports both cgroup v1 and
 * v2 runtimes.
 */
public final class ContainerResource {

  static final Filesystem FILESYSTEM_INSTANCE = new Filesystem();
  private static final Resource INSTANCE = buildSingleton();

  private static Resource buildSingleton() {
    // can't initialize this statically without running afoul of animalSniffer on paths
    return new ContainerResource().buildResource();
  }

  private final CgroupV1ContainerIdExtractor v1Extractor;
  private final CgroupV2ContainerIdExtractor v2Extractor;

  private ContainerResource() {
    this(new CgroupV1ContainerIdExtractor(), new CgroupV2ContainerIdExtractor());
  }

  // Visible for testing
  ContainerResource(
      CgroupV1ContainerIdExtractor v1Extractor, CgroupV2ContainerIdExtractor v2Extractor) {
    this.v1Extractor = v1Extractor;
    this.v2Extractor = v2Extractor;
  }

  // Visible for testing
  Resource buildResource() {
    return getContainerId()
        .map(id -> Resource.create(Attributes.of(CONTAINER_ID, id)))
        .orElseGet(Resource::empty);
  }

  private Optional<String> getContainerId() {
    Optional<String> v1Result = v1Extractor.extractContainerId();
    if (v1Result.isPresent()) {
      return v1Result;
    }
    return v2Extractor.extractContainerId();
  }

  /** Returns resource with container information. */
  public static Resource get() {
    return INSTANCE;
  }

  // Exists for testing
  static class Filesystem {

    boolean isReadable(Path path) {
      return Files.isReadable(path);
    }

    @MustBeClosed
    Stream<String> lines(Path path) throws IOException {
      return Files.lines(path);
    }
  }
}
