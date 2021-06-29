/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.bootstrap;

import io.opentelemetry.instrumentation.api.config.Config;

/**
 * This SPI can be used to define which packages/classes belong to the bootstrap classloader: all
 * packages configured here will always be loaded by the bootstrap classloader, even if it is not a
 * parent of the current context classloader.
 *
 * <p><b>IMPORTANT</b>: This SPI cannot add new packages to the bootstrap CL, it only defines those
 * that are already there - the purpose is to make sure they're loaded by the correct classloader.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface BootstrapPackagesConfigurer {

  /**
   * Configure the passed {@code builder} and define which classes should always be loaded by the
   * bootstrap classloader.
   */
  void configure(Config config, BootstrapPackagesBuilder builder);
}
