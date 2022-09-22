/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.javaagent.bootstrap.AgentApi;
import io.opentelemetry.javaagent.tooling.bootstrap.BootstrapPackagesBuilder;
import io.opentelemetry.javaagent.tooling.bootstrap.BootstrapPackagesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * To ensure that the classes we add to bootstrap class loader are available in class loaders that
 * don't delegate all class loading requests to bootstrap class loader e.g. OSGi we need to tell the
 * agent which packages we have added.
 *
 * @see BootstrapPackagesConfigurer
 */
public class DemoBootstrapPackagesProvider implements BootstrapPackagesConfigurer {

  @Override
  public void configure(BootstrapPackagesBuilder builder, ConfigProperties config) {
    builder.add(AgentApi.class.getPackage().getName());
  }
}
