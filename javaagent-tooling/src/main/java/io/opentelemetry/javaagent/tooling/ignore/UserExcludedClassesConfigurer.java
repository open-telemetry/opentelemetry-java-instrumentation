/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.config.AgentDistributionConfig;
import java.util.List;

@AutoService(IgnoredTypesConfigurer.class)
public class UserExcludedClassesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    List<String> excludedClasses =
        AgentDistributionConfig.get().getScalarList("exclude_classes", String.class, emptyList());
    configureInternal(builder, excludedClasses);
  }

  // Visible for testing
  void configureInternal(IgnoredTypesBuilder builder, List<String> excludedClasses) {
    for (String excludedClass : excludedClasses) {
      excludedClass = excludedClass.trim();
      // remove the trailing *
      if (excludedClass.endsWith("*")) {
        excludedClass = excludedClass.substring(0, excludedClass.length() - 1);
      }
      builder.ignoreClass(excludedClass);
    }
  }
}
