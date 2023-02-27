/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bootstrap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collection;

/**
 * This interface exposes a way to define which packages/classes are present in the bootstrap class
 * loader.
 *
 * <p>This interface should not be implemented by the javaagent extension developer - the javaagent
 * will provide the implementation.
 */
public interface BootstrapPackagesBuilder {

  /**
   * Mark {@code classNameOrPrefix} as one that belongs to the bootstrap class loader.
   *
   * @return {@code this}
   */
  @CanIgnoreReturnValue
  BootstrapPackagesBuilder add(String classNameOrPrefix);

  /**
   * Mark all elements of {@code classNamesOrPrefixes} as ones that belongs to the bootstrap class
   * loader.
   *
   * @return {@code this}
   */
  @CanIgnoreReturnValue
  BootstrapPackagesBuilder addAll(Collection<String> classNamesOrPrefixes);
}
