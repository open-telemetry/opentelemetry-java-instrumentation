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
import java.util.Locale;

@AutoService(IgnoredTypesConfigurer.class)
public class UserExcludedClassesConfigurer implements IgnoredTypesConfigurer {

  // visible for tests
  static final String EXCLUDED_CLASSES_CONFIG = "otel.javaagent.exclude-classes";

  @Override
  public void configure(ConfigProperties config, IgnoredTypesBuilder builder) {
    List<String> excludedClasses = config.getList(normalize(EXCLUDED_CLASSES_CONFIG), emptyList());
    for (String excludedClass : excludedClasses) {
      excludedClass = excludedClass.trim();
      // remove the trailing *
      if (excludedClass.endsWith("*")) {
        excludedClass = excludedClass.substring(0, excludedClass.length() - 1);
      }
      builder.ignoreClass(excludedClass);
    }
  }

  // TODO: remove after https://github.com/open-telemetry/opentelemetry-java/issues/4562 is fixed
  private static String normalize(String key) {
    return key.toLowerCase(Locale.ROOT).replace('-', '.');
  }
}
