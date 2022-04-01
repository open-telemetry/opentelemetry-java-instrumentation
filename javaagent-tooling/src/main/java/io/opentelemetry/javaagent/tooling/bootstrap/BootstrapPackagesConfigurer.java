/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bootstrap;

import io.opentelemetry.instrumentation.api.config.Config;

/**
 * This SPI can be used to define which packages/classes belong to the bootstrap class loader: all
 * packages configured here will always be loaded by the bootstrap class loader, even if class
 * loader that initiated loading of the class does not normally delegate to bootstrap class loader.
 *
 * <p><b>IMPORTANT</b>: This SPI cannot add new packages to the bootstrap CL, it only defines those
 * that are already there - the purpose is to make sure they're loaded by the correct class loader.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface BootstrapPackagesConfigurer {

  /**
   * Configure the passed {@code builder} and define which classes should always be loaded by the
   * bootstrap class loader.
   */
  void configure(Config config, BootstrapPackagesBuilder builder);
}
