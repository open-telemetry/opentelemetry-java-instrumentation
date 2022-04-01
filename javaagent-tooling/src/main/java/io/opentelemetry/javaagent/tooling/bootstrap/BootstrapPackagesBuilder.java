/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bootstrap;

import java.util.Collection;

/**
 * This interface exposes a way to define which packages/classes are present in the bootstrap
 * classloader.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation.
 */
public interface BootstrapPackagesBuilder {

  /**
   * Mark {@code classNameOrPrefix} as one that belongs to the bootstrap classloader.
   *
   * @return {@code this}
   */
  BootstrapPackagesBuilder add(String classNameOrPrefix);

  /**
   * Mark all elements of {@code classNamesOrPrefixes} as ones that belongs to the bootstrap
   * classloader.
   *
   * @return {@code this}
   */
  BootstrapPackagesBuilder addAll(Collection<String> classNamesOrPrefixes);
}
