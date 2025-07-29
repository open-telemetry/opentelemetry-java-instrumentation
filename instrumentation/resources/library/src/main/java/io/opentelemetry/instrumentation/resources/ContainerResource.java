/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.MustBeClosed;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factory for {@link Resource} retrieving Container ID information. It supports both cgroup v1 and
 * v2 runtimes.
 */
public final class ContainerResource {

  // copied from ContainerIncubatingAttributes
  private static final AttributeKey<String> CONTAINER_ID = AttributeKey.stringKey("container.id");

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
    private static final Logger logger = Logger.getLogger(Filesystem.class.getName());

    private final Supplier<String> osNameSupplier;

    Filesystem() {
      this(() -> System.getProperty("os.name"));
    }

    Filesystem(Supplier<String> osNameSupplier) {
      this.osNameSupplier = osNameSupplier;
    }

    boolean isReadable(Path path) {
      return Files.isReadable(path);
    }

    @MustBeClosed
    Stream<String> lines(Path path) throws IOException {
      String osName = osNameSupplier.get();
      if (osName.equalsIgnoreCase("z/OS") || osName.equalsIgnoreCase("OS/390")) {
        try {
          // On z/OS the /proc tree is always encoded with IBM1047 (Canonical name: Cp1047).
          return Files.lines(path, Charset.forName("Cp1047"));
        } catch (UnsupportedCharsetException e) {
          // What charsets are available depends on the instance of the JVM
          logger.log(Level.WARNING, "Unable to find charset Cp1047", e);
          return Stream.empty();
        }
      } else {
        return Files.lines(path);
      }
    }

    List<String> lineList(Path path) throws IOException {
      try (Stream<String> lines = lines(path)) {
        return lines.collect(Collectors.toList());
      }
    }
  }
}
