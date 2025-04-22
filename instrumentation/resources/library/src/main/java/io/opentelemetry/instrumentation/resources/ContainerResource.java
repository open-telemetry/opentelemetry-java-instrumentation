/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import com.google.errorprone.annotations.MustBeClosed;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

  static final Filesystem FILESYSTEM_INSTANCE = new Filesystem(Filesystem::getOsName);
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

    public Filesystem(Supplier<String> osNameSupplier) {
      this.osNameSupplier = osNameSupplier;
    }

    boolean isReadable(Path path) {
      return Files.isReadable(path);
    }

    @MustBeClosed
    Stream<String> lines(Path path) throws IOException {

      String osName = osNameSupplier.get();
      if (osName.equalsIgnoreCase("z/OS") || osName.equalsIgnoreCase("OS/390")) {
        return zosLines(path);
      } else {
        return Files.lines(path);
      }
    }

    // This method reads the /proc system using the correct Charset for z/OS
    @MustBeClosed
    Stream<String> zosLines(Path path) throws IOException {

      List<Charset> charsetsToTest = new ArrayList<Charset>();

      // Since this class needs to be compatible with Java9&11, use the 'native.encoding' property
      // to get the nativeCharset on java17+
      // rather than System.console().charset().
      if (System.getProperty("native.encoding") != null) {
        charsetsToTest.add(Charset.forName(System.getProperty("native.encoding")));
      } else {
        // On older javas that property won't exist but the default charset will be the native
        // encoding.
        charsetsToTest.add(Charset.defaultCharset());
      }

      // As a fallback value, add a hardcoded reference to IBM1047 (Canonical name: Cp1047).
      Charset ibm1047 = Charset.forName("Cp1047");
      if (ibm1047 != null) { // safety check
        charsetsToTest.add(ibm1047);
      }

      // The odds of /proc being UTF-8 but the native encoding saying otherwise is extremely low
      // but since UTF-8 is the standard it should be checked.
      charsetsToTest.add(StandardCharsets.UTF_8);

      IOException exception = null;
      for (Charset charset : charsetsToTest) {
        try (BufferedReader charsetTester = Files.newBufferedReader(path, charset)) {
          @SuppressWarnings("unused")
          String line = charsetTester.readLine();

          return Files.lines(path, charset);
        } catch (IOException e) {
          if (exception == null) {
            exception = e;
          } else {
            exception.addSuppressed(e);
          }
        }
      }

      // If none of the charsets matched, log a warning and return an empty Stream
      logger.log(
          Level.WARNING, "Unable to read file " + path.toAbsolutePath().toString(), exception);
      return Stream.empty();
    }

    List<String> lineList(Path path) throws IOException {
      try (Stream<String> lines = lines(path)) {
        return lines.collect(Collectors.toList());
      }
    }

    static String getOsName() {
      return System.getProperty("os.name");
    }
  }
}
