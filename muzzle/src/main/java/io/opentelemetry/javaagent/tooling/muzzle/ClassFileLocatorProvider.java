/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import javax.annotation.Nullable;
import net.bytebuddy.dynamic.ClassFileLocator;

/**
 * This SPI can be used to add additional {@link ClassFileLocator}s to {@link
 * AgentLocationStrategy}.
 */
public interface ClassFileLocatorProvider {

  /** Provide a class loader which can be used to look up bootstrap resources. */
  @Nullable
  ClassFileLocator getClassFileLocator();
}
