/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.MustBeClosed;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.CONTAINER_ID;

/** Factory for {@link Resource} retrieving Container ID information. */
public final class ContainerResource {

  private static final Resource INSTANCE = buildSingleton();

  private static Resource buildSingleton() {
    // can't initialize this statically without running afoul of animalSniffer on paths
    return new ContainerResource().buildResource();
  }

  private final CGroupsV1ContainerIdExtractor v1Extractor;
  private final CGroupsV2ContainerIdExtractor v2Extractor;

  private ContainerResource() {
    this(new CGroupsV1ContainerIdExtractor(), new CGroupsV2ContainerIdExtractor());
  }

  // Visible for testing
  ContainerResource(
      CGroupsV1ContainerIdExtractor v1Extractor, CGroupsV2ContainerIdExtractor v2Extractor) {
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
    if(v1Result.isPresent()){
      return v1Result;
    }
    return v2Extractor.extractContainerId();
  }

  /** Returns resource with container information. */
  public static Resource get() {
    return INSTANCE;
  }

  // Exists for testing
  final static Filesystem FILESYSTEM_INSTANCE = new Filesystem();
  static class Filesystem {
    boolean exists(Path path, LinkOption... options) {
      return Files.exists(path, options);
    }

    boolean isReadable(Path path) {
      return Files.isReadable(path);
    }

    @MustBeClosed
    Stream<String> lines(Path path) throws IOException {
      return Files.lines(path);
    }

  }
}
