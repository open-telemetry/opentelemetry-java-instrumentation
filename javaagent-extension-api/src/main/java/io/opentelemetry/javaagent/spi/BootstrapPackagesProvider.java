/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

import java.util.List;

/**
 * A service provider to allow adding classes from specified package prefixes to the bootstrap
 * classloader. The classes in the bootstrap classloader are available to all instrumentations. This
 * is useful if large number of custom instrumentations are using functionality from common
 * packages.
 */
public interface BootstrapPackagesProvider {

  /**
   * Classes from returned package prefixes will be available in the bootstrap classloader.
   *
   * @return package prefixes.
   */
  List<String> getPackagePrefixes();
}
