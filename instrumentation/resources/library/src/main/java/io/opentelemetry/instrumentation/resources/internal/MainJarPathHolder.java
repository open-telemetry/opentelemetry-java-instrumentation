/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import java.nio.file.Path;
import java.util.Optional;

class MainJarPathHolder {
  private static final Optional<Path> jarPath =
      Optional.ofNullable(new MainJarPathFinder().detectJarPath());

  static Optional<Path> getJarPath() {
    return jarPath;
  }

  private MainJarPathHolder() {}
}
