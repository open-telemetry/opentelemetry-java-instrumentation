/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

public class MainJarPathHolder {

  // visible for testing
  static void resetForTest() {
    detectionResult = Optional.empty();
  }

  private static class DetectionResult {
    private final Optional<Path> jarPath;

    private DetectionResult(Optional<Path> jarPath) {
      this.jarPath = jarPath;
    }
  }

  private static Optional<DetectionResult> detectionResult = Optional.empty();

  private static final Function<MainJarPathFinder, Optional<Path>> jarPath =
      (finder) -> {
        if (!detectionResult.isPresent()) {
          detectionResult =
              Optional.of(new DetectionResult(Optional.ofNullable(finder.detectJarPath())));
        }
        return detectionResult.get().jarPath;
      };

  static Optional<Path> getJarPath(MainJarPathFinder finder) {
    return jarPath.apply(finder);
  }
}
