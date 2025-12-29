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
public class UserExcludedClassesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(IgnoredTypesBuilder builder) {
    List<String> excludedClasses =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
            .getScalarList("exclude_classes", String.class, emptyList());
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
