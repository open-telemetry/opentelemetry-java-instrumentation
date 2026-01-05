/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import java.util.List;

@AutoService(IgnoredTypesConfigurer.class)
public class UserExcludedClassLoadersConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    List<String> excludedClassLoaders =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
            .getScalarList("exclude_class_loaders", String.class, emptyList());
    configureInternal(builder, excludedClassLoaders);
  }

  // Visible for testing
  void configureInternal(IgnoredTypesBuilder builder, List<String> excludedClassLoaders) {
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
