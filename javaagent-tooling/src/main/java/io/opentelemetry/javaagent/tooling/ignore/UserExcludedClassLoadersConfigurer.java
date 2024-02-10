/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;

@AutoService(IgnoredTypesConfigurer.class)
public class UserExcludedClassLoadersConfigurer implements IgnoredTypesConfigurer {

  // visible for tests
  static final String EXCLUDED_CLASS_LOADERS_CONFIG = "otel.javaagent.exclude-class-loaders";

  @Override
  public void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    List<String> excludedClassLoaders = config.getList(EXCLUDED_CLASS_LOADERS_CONFIG, emptyList());
    for (String excludedClassLoader : excludedClassLoaders) {
      excludedClassLoader = excludedClassLoader.trim();
      // remove the trailing *
      if (excludedClassLoader.endsWith("*")) {
        excludedClassLoader = excludedClassLoader.substring(0, excludedClassLoader.length() - 1);
      }
      builder.ignoreClassLoader(excludedClassLoader);
    }
  }
}
