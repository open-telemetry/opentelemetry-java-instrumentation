/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

/** This SPI can be used to find a class loader which can be used to look up bootstrap resources. */
public interface BootstrapProxyProvider {

  /** Provide a class loader which can be used to look up bootstrap resources. */
  ClassLoader getBootstrapProxy();
}
