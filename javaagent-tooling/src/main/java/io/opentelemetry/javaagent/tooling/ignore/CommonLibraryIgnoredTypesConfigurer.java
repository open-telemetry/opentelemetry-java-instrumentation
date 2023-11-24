/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * Unlike the {@link AdditionalLibraryIgnoredTypesConfigurer}, this one is applied to all tests. It
 * should only contain classes that are included in the most commonly used libraries in test (e.g.
 * Spring Boot).
 */
@AutoService(IgnoredTypesConfigurer.class)
public class CommonLibraryIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    builder.ignoreClass("org.springframework.boot.autoconfigure.ssl.FileWatcher$WatcherThread");
  }
}
